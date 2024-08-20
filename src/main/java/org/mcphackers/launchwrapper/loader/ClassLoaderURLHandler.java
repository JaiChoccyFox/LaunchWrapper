package org.mcphackers.launchwrapper.loader;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public class ClassLoaderURLHandler extends URLStreamHandler {

    private LaunchClassLoader classLoader;

    public ClassLoaderURLHandler(LaunchClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    class ClassLoaderURLConnection extends URLConnection {

        protected ClassLoaderURLConnection(URL url) {
            super(url);
        }

        @Override
        public void connect() throws IOException {
        }
        
        @Override
        public InputStream getInputStream() throws IOException {
            String path = url.getPath();
            ClassNode node = classLoader.overridenClasses.get(path);
            if(node == null) {
                throw new FileNotFoundException();
            }
			ClassWriter writer = new SafeClassWriter(classLoader, COMPUTE_MAXS | COMPUTE_FRAMES);
			node.accept(writer);
			byte[] classData = writer.toByteArray();
            return new ByteArrayInputStream(classData);
        }

    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        return new ClassLoaderURLConnection(url);
    }
    
}
