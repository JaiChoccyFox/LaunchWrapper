package org.mcphackers.launchwrapper.loader;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.mcphackers.launchwrapper.inject.ClassNodeSource;
import org.mcphackers.launchwrapper.tweak.ClassLoaderTweak;
import org.mcphackers.launchwrapper.util.Util;
import org.mcphackers.rdi.util.NodeHelper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

// URLClassLoader is required to support ModLoader loading mods from mod folder
public class LaunchClassLoader extends URLClassLoader implements ClassNodeSource {

	public static final int CLASS_VERSION = getSupportedClassVersion();
	private static LaunchClassLoader INSTANCE;

	private ClassLoader parent;
	private ClassLoaderTweak tweak;
	private Map<String, Class<?>> exceptions = new HashMap<String, Class<?>>();
	/** Keys should contain dots */
	private Map<String, Class<?>> classes = new HashMap<String, Class<?>>();
	/** Keys should contain dots */
	private Map<String, ClassNode> overridenClasses = new HashMap<String, ClassNode>();
	/** Keys should contain slashes */
	private Map<String, ClassNode> classNodeCache = new HashMap<String, ClassNode>();
	private File debugOutput;

	public LaunchClassLoader(ClassLoader parent) {
		super(new URL[0], null);
		this.parent = parent;
	}

	public void setClassPath(URL[] urls) {
		for(URL url : urls) {
			super.addURL(url);
		}
	}
	
	public void setDebugOutput(File directory) {
		debugOutput = directory;
	}

    protected void addURL(URL url) {
    	super.addURL(url);
    }

	public URL getResource(String name) {
		URL url = super.getResource(name);
		if(url != null) {
			return url;
		}
		return parent.getResource(name);
	}

	public Enumeration<URL> getResources(String name) throws IOException {
		//TODO ?
		return parent.getResources(name);
	}

	public Class<?> findClass(String name) throws ClassNotFoundException {
		if(name.startsWith("java.")) {
			return parent.loadClass(name);
		}
		name = className(name);
		Class<?> cls;
		cls = exceptions.get(name);
		if(cls != null) {
			return cls;
		}
		cls = classes.get(name);
		if(cls != null) {
			return cls;
		}
		cls = transformedClass(name);
		if(cls != null) {
			return cls;
		}
		throw new ClassNotFoundException(name);
	}

	public void invokeMain(String launchTarget, String... args) {
		classNodeCache.clear();
		try {
			Class<?> mainClass = findClass(launchTarget);
			mainClass.getDeclaredMethod("main", String[].class).invoke(null, (Object) args);
		} catch (InvocationTargetException e1) {
			if(e1.getCause() != null) {
				e1.getCause().printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private ProtectionDomain getProtectionDomain(String name) {
		final URL resource = getResource(classResourceName(name));
		if(resource == null) {
			return null;
		}
		CodeSource codeSource;
		if(resource.getProtocol().equals("jar")) {
			String path = resource.getPath();
			if(path.startsWith("file:")) {
				path = path.substring("file:".length());
				int i = path.lastIndexOf('!');
				if(i != -1) {
					path.substring(0, i);
				}
			}
			try {
				URL newResource = new URL("file", "", path);
				codeSource = new CodeSource(newResource, new Certificate[0]);
			} catch (MalformedURLException e) {
				codeSource = new CodeSource(resource, new Certificate[0]);
			}
		} else {
			codeSource = new CodeSource(resource, new Certificate[0]);
		}
		return new ProtectionDomain(codeSource, null);
	}

	public void overrideClass(ClassNode node) {
		if(node == null)
			return;
		saveDebugClass(node);
		overridenClasses.put(className(node.name), node);
		classNodeCache.put(node.name, node);
	}

	private void saveDebugClass(ClassNode node) {
		if(debugOutput == null) {
			return;
		}
		ClassWriter writer = new SafeClassWriter(this, COMPUTE_MAXS);
		node.accept(writer);
		byte[] classData = writer.toByteArray();
		File cls = new File(debugOutput, node.name + ".class");
		cls.getParentFile().mkdirs();
		try {
			FileOutputStream fos = new FileOutputStream(cls);
			fos.write(classData);
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param name
	 *            can be specified either as net.minecraft.client.Minecraft or as
	 *            net/minecraft/client/Minecraft
	 * @return parsed ClassNode
	 */
	public ClassNode getClass(String name) {
		ClassNode node = classNodeCache.get(classNodeName(name));
		if(node != null) {
			return node;
		}
		byte[] classData = getClassAsBytes(name);
		if(classData == null)
			return null;
		ClassNode classNode = new ClassNode();
		ClassReader classReader = new ClassReader(classData);
		classReader.accept(classNode, 0);
		classNodeCache.put(classNode.name, classNode);
		return classNode;
	}

	public FieldNode getField(String owner, String name, String desc) {
		return NodeHelper.getField(getClass(owner), name, desc);
	}

	public MethodNode getMethod(String owner, String name, String desc) {
		return NodeHelper.getMethod(getClass(owner), name, desc);
	}

	protected Class<?> redefineClass(String name) {
		ClassNode classNode = getClass(name);
		if(classNode != null && tweak != null && tweak.tweakClass(classNode)) {
			return redefineClass(classNode);
		}
		byte[] classData = getClassAsBytes(name);
		if(classData == null) {
			return null;
		}
		Class<?> definedClass = defineClass(name, classData, 0, classData.length, getProtectionDomain(name));
		classes.put(name, definedClass);
		return definedClass;
	}

	private Class<?> redefineClass(ClassNode node) {
		if(node == null) {
			return null;
		}
		ClassWriter writer = new SafeClassWriter(this, COMPUTE_MAXS | COMPUTE_FRAMES);
		node.accept(writer);
		byte[] classData = writer.toByteArray();
		String name = className(node.name);
		Class<?> definedClass = defineClass(name, classData, 0, classData.length, getProtectionDomain(name));
		classes.put(name, definedClass);
		return definedClass;
	}

	private static String className(String nameWithSlashes) {
		return nameWithSlashes.replace('/', '.');
	}

	private static String classNodeName(String nameWithDots) {
		return nameWithDots.replace('.', '/');
	}

	private static String classResourceName(String name) {
		return name.replace('.', '/') + ".class";
	}

	private InputStream getClassAsStream(String name) {
		String className = classResourceName(name);
		InputStream is = super.getResourceAsStream(className);
		if(is != null) {
			return is;
		}
		return parent.getResourceAsStream(className);
	}

	private byte[] getClassAsBytes(String name) {
		InputStream is = getClassAsStream(name);
		if(is == null)
			return null;
		byte[] classData;
		try {
			classData = Util.readStream(is);
		} catch (IOException e) {
			Util.closeSilently(is);
			return null;
		}
		Util.closeSilently(is);
		return classData;
	}

	private Class<?> transformedClass(String name) {
		ClassNode transformed = overridenClasses.get(name);
		if(transformed != null) {
			if(tweak != null) {
				tweak.tweakClass(transformed);
			}
			return redefineClass(transformed);
		}
		return redefineClass(name);
	}

	public void addException(Class<?> cls) {
		exceptions.put(cls.getName(), cls);
	}

	public void setLoaderTweak(ClassLoaderTweak classLoaderTweak) {
		tweak = classLoaderTweak;
	}

	private static ClassNode getSystemClass(Class<?> cls) {
		InputStream is = ClassLoader.getSystemResourceAsStream(classResourceName(cls.getName()));
		if(is == null)
			return null;
		try {
			ClassNode classNode = new ClassNode();
			ClassReader classReader = new ClassReader(is);
			classReader.accept(classNode, 0);
			return classNode;
		} catch (IOException e) {
			Util.closeSilently(is);
			return null;
		}
	}

	private static int getSupportedClassVersion() {
		ClassNode objectClass = getSystemClass(Object.class);
		return objectClass.version;
	}

	public static LaunchClassLoader instantiate() {
		if(INSTANCE != null) {
			throw new IllegalStateException("Can only have one instance of LaunchClassLoader!");
		}
		return INSTANCE = new LaunchClassLoader(getSystemClassLoader());
	}

}
