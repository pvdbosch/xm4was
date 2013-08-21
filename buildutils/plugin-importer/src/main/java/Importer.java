import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.Publisher;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.github.veithen.cosmos.osgi.runtime.Configuration;
import com.github.veithen.cosmos.osgi.runtime.Runtime;
import com.github.veithen.cosmos.osgi.runtime.logging.simple.SimpleLogger;
import com.github.veithen.cosmos.p2.P2Initializer;
import com.github.veithen.cosmos.p2.SystemOutProgressMonitor;
import com.google.common.io.Files;

public class Importer {
    private static final Set<String> ignore = new HashSet<String>(Arrays.asList("META-INF/ECLIPSEF.RSA", "META-INF/ECLIPSEF.SF"));
    
    public static void main(String[] args) throws Exception {
        File outputDir = Files.createTempDir();
        IPublisherAction[] publisherActions = new IPublisherAction[args.length-1];
        for (int i=0; i<args.length-1; i++) {
            publisherActions[i] = new BundlesAction(processPlugins(new File(args[i]), outputDir));
        }
        
        Runtime runtime = Runtime.getInstance(Configuration.newDefault().logger(SimpleLogger.INSTANCE).initializer(new P2Initializer(new File("p2-data"))).build());
        IProvisioningAgent agent = runtime.getService(IProvisioningAgent.class);
        IArtifactRepositoryManager repoman = (IArtifactRepositoryManager)agent.getService(IArtifactRepositoryManager.SERVICE_NAME);
        IArtifactRepository repository = repoman.createRepository(new URI("file:///home/veithen/was_repo2"), "WebSphere Repository", IArtifactRepositoryManager.TYPE_SIMPLE_REPOSITORY, Collections.<String,String>emptyMap());
        PublisherInfo publisherInfo = new PublisherInfo();
        publisherInfo.setArtifactRepository(repository);
        publisherInfo.setArtifactOptions(IPublisherInfo.A_PUBLISH | IPublisherInfo.A_INDEX);
        Publisher publisher = new Publisher(publisherInfo);
        publisher.publish(publisherActions, new SystemOutProgressMonitor());
        // TODO: need a shutdown method for the OSGi runtime (to stop non daemon threads)
        System.exit(0);
    }
    
    private static File[] processPlugins(final File wasDir, File outputDir) throws Exception {
        List<File> outputFiles = new ArrayList<File>();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setValidating(false);
        DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
        documentBuilder.setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
                if (systemId != null && systemId.endsWith("/product.dtd")) {
                    return new InputSource(new File(wasDir, "properties/version/dtd/product.dtd").toURI().toString());
                } else {
                    return null;
                }
            }
        });
        Document document = documentBuilder.parse(new File(wasDir, "properties/version/WAS.product"));
        String wasVersion = ((Element)document.getElementsByTagName("version").item(0)).getTextContent();
        System.out.println("WAS version is " + wasVersion);
        String bundleVersionSuffix = "WAS_" + wasVersion.replace(".", "_");
        byte[] buffer = new byte[4096];
        for (File plugin : new File(wasDir, "plugins").listFiles()) {
            String name = plugin.getName();
            if (plugin.isFile() && name.endsWith(".jar")) {
                System.out.println(plugin);
                ZipInputStream zin = new ZipInputStream(new FileInputStream(plugin));
                File outputFile = new File(outputDir, name.substring(0, name.length()-4) + "_" + wasVersion + ".jar");
                outputFiles.add(outputFile);
                ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(outputFile));
                ZipEntry entry;
                while ((entry = zin.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (ignore.contains(entryName)) {
                        continue;
                    }
                    zout.putNextEntry(new ZipEntry(entryName));
                    if (entryName.equals("META-INF/MANIFEST.MF")) {
                        Manifest manifest = new Manifest(zin);
                        Attributes atts = manifest.getMainAttributes();
                        String bundleVersion = atts.getValue("Bundle-Version");
                        int dots = 0;
                        for (int i=0; i<bundleVersion.length(); i++) {
                            if (bundleVersion.charAt(i) == '.') {
                                dots++;
                            }
                        }
                        atts.putValue("Bundle-Version", bundleVersion + (dots == 3 ? "_" : (dots == 2 ? "." : ".0.")) + bundleVersionSuffix);
                        // Remove signatures
                        manifest.getEntries().clear();
                        manifest.write(zout);
                    } else {
                        int c;
                        while ((c = zin.read(buffer)) != -1) {
                            zout.write(buffer, 0, c);
                        }
                    }
                }
                zin.close();
                zout.close();
            }
        }
        return outputFiles.toArray(new File[outputFiles.size()]);
    }
}