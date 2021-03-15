package org.eclipse.openj9.jmin.plugins;

import org.eclipse.openj9.jmin.info.ClassInfo;
import org.eclipse.openj9.jmin.info.MethodInfo;
import org.eclipse.openj9.jmin.info.ReferenceInfo;
import org.eclipse.openj9.jmin.util.WorkList;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static javax.xml.parsers.DocumentBuilderFactory.newInstance;

public class OsgiSCRXmlFileProcessor {
    private WorkList worklist;
    private ReferenceInfo info;
    private String fileName;
    private InputStream inputStream;
    private static DocumentBuilder docBuilder = null;

    static {
        try {
            docBuilder = newInstance().newDocumentBuilder();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public OsgiSCRXmlFileProcessor(WorkList worklist, ReferenceInfo info, String fileName, InputStream is) {
        this.worklist = worklist;
        this.info = info;
        this.fileName = fileName;

        /**
         *  Read the file contents to create another InputStream because the DOM parser
         *  closes the underlying InputStream, which we don't want.
         */
        int count = 0;
        byte buffer[] = new byte[512];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            while ((count = is.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            this.inputStream = new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void process() {
        try {
            Document doc = docBuilder.parse(inputStream);
            NodeList implementation = doc.getElementsByTagName("implementation");
            if (implementation != null && implementation.getLength() > 0) {
                Element impl = (Element) implementation.item(0);
                String implClass = impl.getAttribute("class").replace('.', '/');
                worklist.forceInstantiateClass(implClass);
                ClassInfo cinfo = info.getClassInfo(implClass);
                for (MethodInfo minfo: cinfo.getMethodInfoList()) {
                    worklist.processMethod(implClass, minfo.name(), minfo.desc());
                }
                docBuilder.reset();
            }
        } catch (IOException | SAXException e) {
            e.printStackTrace();
        }
    }
}
