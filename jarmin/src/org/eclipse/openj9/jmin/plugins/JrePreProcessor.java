package org.eclipse.openj9.jmin.plugins;

import org.eclipse.openj9.jmin.info.ClassInfo;
import org.eclipse.openj9.jmin.info.ReferenceInfo;
import org.eclipse.openj9.jmin.util.HierarchyContext;
import org.eclipse.openj9.jmin.util.WorkList;

import java.util.Set;

public class JrePreProcessor extends PreProcessor {
    public JrePreProcessor(WorkList worklist, HierarchyContext context, ReferenceInfo info) {
        super(worklist, context, info);
    }

    @Override
    public void process() {
        Set<String> implementors = context.getInterfaceImplementors("java/security/PrivilegedAction");
        if (implementors.size() > 0) {
            for (String i : implementors) {
                worklist.forceInstantiateClass(i);
            }
            worklist.processInterfaceMethod("java/security/PrivilegedAction", "run", "()Ljava/lang/Object;");
        }

        Set<String> subclasses = context.getSubClasses("java/util/ResourceBundle");
        if (subclasses.size() > 0) {
            for (String clazz : subclasses) {
                worklist.forceInstantiateClass(clazz);
            }
        }

        subclasses = context.getSubClasses("java/util/logging/LogManager");
        if (subclasses.size() > 0) {
            for (String clazz : subclasses) {
                worklist.forceInstantiateClass(clazz);
            }
        }
    }
}
