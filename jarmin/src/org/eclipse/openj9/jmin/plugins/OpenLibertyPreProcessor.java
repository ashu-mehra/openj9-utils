package org.eclipse.openj9.jmin.plugins;

import org.eclipse.openj9.jmin.info.ReferenceInfo;
import org.eclipse.openj9.jmin.util.HierarchyContext;
import org.eclipse.openj9.jmin.util.WorkList;

public class OpenLibertyPreProcessor extends PreProcessor {

    public OpenLibertyPreProcessor(WorkList worklist, HierarchyContext context, ReferenceInfo info) {
        super(worklist, context, info);
    }

    @Override
    public void process() {
        /**
         * LauncherDelegateImpl is identified as referenced but its constructor is not marked for processing
         * as the its instance gets created via reflection com.ibm.ws.kernel.boot.internal.KernelBootstrap.go()
         */
        worklist.forceInstantiateClass("com/ibm/ws/kernel/launch/internal/LauncherDelegateImpl");
        /**
         * LogProviderImpl is referenced in wlp/lib/platform/defaultLogging-1.0.mf and is probably obtained in
         * KernelResolver.<init> by calling
         * com/ibm/ws/kernel/boot/internal/KernelResolver$ManifestCacheElement.getLogProviderClass()
         * which in turn obtains it from its <init>.
         */
        worklist.forceInstantiateClass("com/ibm/ws/logging/internal/impl/LogProviderImpl");

        /**
         * BaseTraceService and  BaseFFDCService gets loaded and instantiated by the call to
         * LoggingConfigUtils.getDelegate() method in LogProviderConfigImpl.<init>.
         * Ideally it should have been identified by MethodSummary process as the string for the class BaseTraceService
         * is passed by the caller as a parameter to the callee, but the instantiation happens by assigning the string
         * to a different argument, as in:
         *     public static <T> T getDelegate(Class<T> delegateClass, String className, String defaultDelegateClass) {
         *     if (className == null)
         *         className = defaultDelegateClass;
         *     try {
         *         return Class.forName(className).asSubclass(delegateClass).newInstance();
         *     } ...
         */
        worklist.forceInstantiateClass("com/ibm/ws/logging/internal/impl/BaseTraceService");
        worklist.forceInstantiateClass("com/ibm/ws/logging/internal/impl/BaseFFDCService");

        /**
         * EnvCheck.main() gets called via Method.invoke() but the EnvCheck.class is obtained by reading Manifest file
         * in SelfExtractRun.runServerInline(), because of which we fail to identify the call to EnvCheck.main().
         */
        worklist.processMethod("com/ibm/ws/kernel/boot/cmdline/EnvCheck", "main", "([Ljava/lang/String;)V");

        /**
         * In source code it is referenced in com.ibm.ws.crypto.passwordutil/bnd.bnd.
         * In compiled code it appears in com.ibm.ws.crypto.passwordutil_1.0.43.jar/OSGI-INF/keyResolver.xml.
         */
        worklist.forceInstantiateClass("com/ibm/ws/crypto/util/VariableResolver");

        /**
         * In source code it is referenced incom.ibm.ws.logging.osgi/bnd.bnd.
         */
        worklist.forceInstantiateClass("com/ibm/ws/logging/internal/osgi/FFDCJanitor");

        /**
         * In source code it is referenced incom.ibm.ws.threading/bnd.bnd.
         */
        worklist.forceInstantiateClass("com/ibm/ws/threading/internal/DeferrableScheduledExecutorImpl");

        /**
         * In source code it is referenced incom.ibm.ws.threading/bnd.bnd.
         */
        worklist.forceInstantiateClass("com/ibm/ws/threading/internal/ScheduledExecutorImpl");

        /**
         * In source code it is referenced incom.ibm.ws.threading/bnd.bnd.
         */
        worklist.forceInstantiateClass("com/ibm/ws/threading/internal/FutureMonitorImpl");

        /**
         * WsLogger gets instantiated in WsLogManager.getLogger().
         */
        worklist.forceInstantiateClass("com/ibm/ws/logging/internal/WsLogger");

        /**
         * Source: unknown
         */
        worklist.forceInstantiateClass("org/osgi/service/cm/ConfigurationException");

        /**
         * Referenced by com.ibm.tx.util.logging.Tr.reinitializeTracer() but the ReflectionInterpreter fails to
         * identify as the type information is lost during merge operation if any of the type is not known.
         * TODO: Maintaining multi-type value can help in this case.
         */
        worklist.forceInstantiateClass("com/ibm/ws/tx/util/logging/WASTr");

        /**
         * These classes are referenced in com.ibm.ws.http.internal.HttpChannelProvider.<init> method
         */
        worklist.forceInstantiateClass("com/ibm/ws/http/channel/internal/inbound/HttpInboundChannelFactory");
        worklist.forceInstantiateClass("com/ibm/ws/http/channel/internal/outbound/HttpOutboundChannelFactory");
        worklist.forceInstantiateClass("com/ibm/ws/http/dispatcher/internal/channel/HttpDispatcherFactory");

        /**
         * com.ibm.ws.kernel.boot.jmx.internal.PlatformMBeanServerBuilder is referenced by system property
         * javax.management.builder.initial.
         */
        worklist.forceInstantiateClass("com/ibm/ws/kernel/boot/jmx/internal/PlatformMBeanServerBuilder");

        worklist.forceInstantiateClass("com/ibm/ws/tcpchannel/internal/TCPChannelFactory");
        worklist.forceInstantiateClass("com/ibm/ws/microprofile/metrics/publicapi/PublicMetricsRESTProxyServlet");
    }
}
