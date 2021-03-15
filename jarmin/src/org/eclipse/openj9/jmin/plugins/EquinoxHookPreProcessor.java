package org.eclipse.openj9.jmin.plugins;

import org.eclipse.openj9.jmin.info.ReferenceInfo;
import org.eclipse.openj9.jmin.util.HierarchyContext;
import org.eclipse.openj9.jmin.util.WorkList;

import java.util.Set;

public class EquinoxHookPreProcessor extends PreProcessor {

    public EquinoxHookPreProcessor(WorkList worklist, HierarchyContext context, ReferenceInfo info) {
        super(worklist, context, info);
    }
    public void process() {
        Set<String> implementors = context.getInterfaceImplementors("org/eclipse/osgi/internal/hookregistry/BundleFileWrapperFactoryHook");
        if (implementors.size() > 0) {
            for (String i : implementors) {
                worklist.forceInstantiateClass(i);
            }
            worklist.processInterfaceMethod("org/eclipse/osgi/baseadaptor/hooks/BundleFileWrapperFactoryHook",
                    "wrapBundleFile",
                    "(Lorg/eclipse/osgi/storage/bundlefile/BundleFile;Lorg/eclipse/osgi/storage/BundleInfo$Generation;Z)Lorg/eclipse/osgi/storage/bundlefile/BundleFileWrapper;");
        }
    }
}
