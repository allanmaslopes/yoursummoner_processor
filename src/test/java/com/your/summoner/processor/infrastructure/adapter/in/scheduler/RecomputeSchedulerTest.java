package com.your.summoner.processor.infrastructure.adapter.in.scheduler;

import com.your.summoner.processor.application.port.out.KnowledgeStore;
import com.your.summoner.processor.application.port.out.MatchSource;
import com.your.summoner.processor.application.service.RecomputeService;
import com.your.summoner.processor.domain.model.MatchRecord;
import com.your.summoner.processor.domain.model.PatchVersion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import static com.your.summoner.processor.support.RecomputeFixtures.catalog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RecomputeSchedulerTest {
    @Test void disabledInTestProfile() {
        var recomputations = new AtomicInteger();
        var runner = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(RecomputeScheduler.class, SchedulingConfiguration.class)
                .withBean(RecomputeService.class, () -> service(recomputations))
                .withBean(CapturingScheduler.class, CapturingScheduler::new)
                .withPropertyValues("spring.profiles.active=test", "processor.cron=0 7 2 * * *");

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("processor.enabled", Boolean.class)).isFalse();
            assertThat(context).doesNotHaveBean(RecomputeScheduler.class);
            context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
                    context.getSourceApplicationContext(), Duration.ZERO));
            assertThat(recomputations).hasValue(0);
            assertThat(context.getBean(CapturingScheduler.class).task).isNull();
        });
        runner.withPropertyValues("processor.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RecomputeScheduler.class);
            assertThat(recomputations).hasValue(0);
            context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
                    context.getSourceApplicationContext(), Duration.ZERO));
            awaitRecomputations(recomputations, 1);

            var scheduler = context.getBean(CapturingScheduler.class);
            assertThat(scheduler.trigger).isInstanceOfSatisfying(CronTrigger.class,
                    trigger -> assertThat(trigger.getExpression()).isEqualTo("0 7 2 * * *"));
            assertThat(scheduler.task).isNotNull();
            scheduler.task.run();
            assertThat(recomputations).hasValue(2);
        });
    }

    @Test void startupFailureDoesNotStopApplication() throws Exception {
        var attempted = new CountDownLatch(1);
        var failing = new RecomputeService(new MatchSource() {
            @Override public Stream<MatchRecord> byPatch(PatchVersion patch) { return Stream.empty(); }
            @Override public List<PatchVersion> patches() {
                attempted.countDown();
                throw new IllegalStateException("mongo down");
            }
        }, patch -> 0, patch -> catalog(), new NoStore(), 1, 1, 5);
        var scheduler = new RecomputeScheduler(failing);

        assertThatCode(scheduler::onStartup).doesNotThrowAnyException();
        assertThat(attempted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThatCode(scheduler::recompute).doesNotThrowAnyException();
    }

    private static void awaitRecomputations(AtomicInteger recomputations, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (recomputations.get() < expected && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(recomputations).hasValue(expected);
    }

    private static final class NoStore implements KnowledgeStore {
        @Override public void publish(Snapshot snapshot) { throw new AssertionError(); }
        @Override public Optional<Snapshot> find(PatchVersion patch) { return Optional.empty(); }
        @Override public List<PatchVersion> patches() { return List.of(); }
        @Override public void remove(PatchVersion patch) { throw new AssertionError(); }
    }

    private static RecomputeService service(AtomicInteger recomputations) {
        var source = new MatchSource() {
            @Override public Stream<MatchRecord> byPatch(PatchVersion patch) { return Stream.empty(); }
            @Override public List<PatchVersion> patches() {
                recomputations.incrementAndGet();
                return List.of();
            }
        };
        var store = new KnowledgeStore() {
            @Override public void publish(Snapshot snapshot) { throw new AssertionError("unexpected publication"); }
            @Override public Optional<Snapshot> find(PatchVersion patch) { return Optional.empty(); }
            @Override public List<PatchVersion> patches() { return List.of(); }
            @Override public void remove(PatchVersion patch) { throw new AssertionError("unexpected removal"); }
        };
        return new RecomputeService(source, patch -> 0, patch -> catalog(), store, 1, 1, 5);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingConfiguration {}

    static final class CapturingScheduler extends ConcurrentTaskScheduler {
        Runnable task;
        Trigger trigger;

        @Override public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            this.task = task;
            this.trigger = trigger;
            return new PendingTask(task);
        }
    }

    private static final class PendingTask extends FutureTask<Void> implements ScheduledFuture<Void> {
        PendingTask(Runnable task) { super(task, null); }
        @Override public long getDelay(TimeUnit unit) { return Long.MAX_VALUE; }
        @Override public int compareTo(Delayed other) { return 0; }
    }
}
