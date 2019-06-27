package com.squareup.leakcanary;

import android.content.Context;
import android.support.annotation.NonNull;

import com.squareup.leakcanary.internal.DisplayLeakActivity;
import com.squareup.leakcanary.internal.FragmentRefWatcher;
import com.squareup.leakcanary.internal.LeakCanaryInternals;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.squareup.leakcanary.RefWatcher.DISABLED;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A {@link RefWatcherBuilder} with appropriate Android defaults.
 */
public final class AndroidRefWatcherBuilder extends RefWatcherBuilder<AndroidRefWatcherBuilder> {

    private static final long DEFAULT_WATCH_DELAY_MILLIS = SECONDS.toMillis(5);

    private final Context context;
    private boolean watchActivities = true;
    private boolean watchFragments = true;
    private boolean enableDisplayLeakActivity = false;

    AndroidRefWatcherBuilder(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Sets a custom {@link AbstractAnalysisResultService} to listen to analysis results. This
     * overrides any call to {@link #heapDumpListener(HeapDump.Listener)}.
     */
    public @NonNull
    AndroidRefWatcherBuilder listenerServiceClass(
            @NonNull Class<? extends AbstractAnalysisResultService> listenerServiceClass) {
        enableDisplayLeakActivity = DisplayLeakService.class.isAssignableFrom(listenerServiceClass);
        return heapDumpListener(new ServiceHeapDumpListener(context, listenerServiceClass));
    }

    /**
     * Sets a custom delay for how long the {@link RefWatcher} should wait until it checks if a
     * tracked object has been garbage collected. This overrides any call to {@link
     * #watchExecutor(WatchExecutor)}.
     */
    public @NonNull
    AndroidRefWatcherBuilder watchDelay(long delay, @NonNull TimeUnit unit) {
        return watchExecutor(new AndroidWatchExecutor(unit.toMillis(delay)));
    }

    /**
     * Whether we should automatically watch activities when calling {@link #buildAndInstall()}.
     * Default is true.
     */
    public @NonNull
    AndroidRefWatcherBuilder watchActivities(boolean watchActivities) {
        this.watchActivities = watchActivities;
        return this;
    }

    /**
     * Whether we should automatically watch fragments when calling {@link #buildAndInstall()}.
     * Default is true. When true, LeakCanary watches native fragments on Android O+ and support
     * fragments if the leakcanary-support-fragment dependency is in the classpath.
     */
    public @NonNull
    AndroidRefWatcherBuilder watchFragments(boolean watchFragments) {
        this.watchFragments = watchFragments;
        return this;
    }

    /**
     * Sets the maximum number of heap dumps stored. This overrides any call to
     * {@link LeakCanary#setLeakDirectoryProvider(LeakDirectoryProvider)}
     *
     * @throws IllegalArgumentException if maxStoredHeapDumps < 1.
     */
    public @NonNull
    AndroidRefWatcherBuilder maxStoredHeapDumps(int maxStoredHeapDumps) {
        LeakDirectoryProvider leakDirectoryProvider =
                new DefaultLeakDirectoryProvider(context, maxStoredHeapDumps);
        LeakCanary.setLeakDirectoryProvider(leakDirectoryProvider);
        return self();
    }

    /**
     * Creates a {@link RefWatcher} instance and makes it available through {@link
     * LeakCanary#installedRefWatcher()}.
     * <p>
     * Also starts watching activity references if {@link #watchActivities(boolean)} was set to true.
     *
     * @throws UnsupportedOperationException if called more than once per Android process.
     */
    @NonNull
    public RefWatcher buildAndInstall() {

        //install()方法只能一次调用，多次调用将抛出异常
        if (LeakCanaryInternals.installedRefWatcher != null) {
            throw new UnsupportedOperationException("buildAndInstall() should only be called once.");
        }

        //初始化RefWatcher，这个东西是用来检查内存泄漏的，包括解析堆转储文件这些东西
        RefWatcher refWatcher = build();

        //如果RefWatcher还没有初始化，就会进入这个分支
        if (refWatcher != DISABLED) {
            if (enableDisplayLeakActivity) {
                //setEnabledAsync最终调用了packageManager.setComponentEnabledSetting,
                // 将Activity组件设置为可用，即在manifest中enable属性。
                // 也就是说，当我们运行LeakCanary.install(this)的时候,LeakCanary的icon才显示出来
                LeakCanaryInternals.setEnabledAsync(context, DisplayLeakActivity.class, true);
            }


            //ActivityRefWatcher.install和FragmentRefWatcher.Helper.install的功能差不多，注册了生命周期监听。
            // 不同的是，前者用application监听Activity的生命周期，后者用Activity监听也就是Activity回调onActivityCreated方法，
            // 然后获取FragmentManager调用registerFragmentLifecycleCallbacks方法注册，监听fragment的生命周期，
            // 而且用到了leakcanary-support-fragment包，兼容了v4的fragment。
            if (watchActivities) {
                ActivityRefWatcher.install(context, refWatcher);
            }
            if (watchFragments) {
                FragmentRefWatcher.Helper.install(context, refWatcher);
            }
        }
        LeakCanaryInternals.installedRefWatcher = refWatcher;
        return refWatcher;
    }

    @Override
    protected boolean isDisabled() {
        return LeakCanary.isInAnalyzerProcess(context);
    }

    @Override
    protected @NonNull
    HeapDumper defaultHeapDumper() {
        LeakDirectoryProvider leakDirectoryProvider =
                LeakCanaryInternals.getLeakDirectoryProvider(context);
        return new AndroidHeapDumper(context, leakDirectoryProvider);
    }

    @Override
    protected @NonNull
    DebuggerControl defaultDebuggerControl() {
        return new AndroidDebuggerControl();
    }

    @Override
    protected @NonNull
    HeapDump.Listener defaultHeapDumpListener() {
        return new ServiceHeapDumpListener(context, DisplayLeakService.class);
    }

    @Override
    protected @NonNull
    ExcludedRefs defaultExcludedRefs() {
        return AndroidExcludedRefs.createAppDefaults().build();
    }

    @Override
    protected @NonNull
    WatchExecutor defaultWatchExecutor() {
        return new AndroidWatchExecutor(DEFAULT_WATCH_DELAY_MILLIS);
    }

    @Override
    protected @NonNull
    List<Class<? extends Reachability.Inspector>> defaultReachabilityInspectorClasses() {
        return AndroidReachabilityInspectors.defaultAndroidInspectors();
    }
}
