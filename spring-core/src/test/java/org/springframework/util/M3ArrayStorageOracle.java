/*
 * Copyright 2002-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project

package org.springframework.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import org.jspecify.annotations.Nullable;

/**
 * Independently compile the exact preimages of the two array storage owners.
 *
 * @author Hitesh Soliwal and Contributors to the Synexia Project
 * @since 7.1
 */
public final class M3ArrayStorageOracle implements AutoCloseable {

	private static final Map<String, String> SOURCE_HASHES = Map.of(
			"org.springframework.util.CompositeIterator",
			"f280d0d54d8f84de7d597ace9912378cf37e27f7ccdab1c47c4372ce6bbe0210",
			"org.springframework.aot.hint.JdkProxyHint",
			"7475347331e29f4340b192d044bd4d144f6a1901c59d343330cc5d48004ed502");

	private final URLClassLoader loader;


	public M3ArrayStorageOracle(Path work) throws Exception {
		var compiler = ToolProvider.getSystemJavaCompiler();
		Assert.state(compiler != null, "A JDK is required for the original-source proof");
		List<Path> sources = new ArrayList<>();
		for (Map.Entry<String, String> entry : SOURCE_HASHES.entrySet()) {
			String leaf = entry.getKey().substring(entry.getKey().lastIndexOf('.') + 1) + ".java";
			byte[] bytes;
			try (InputStream input = getClass().getResourceAsStream(
					"/m3/array-storage-20261019/original/" + leaf + ".txt")) {
				Assert.state(input != null, "Missing original " + leaf);
				bytes = input.readAllBytes();
			}
			String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			Assert.state(entry.getValue().equals(hash), "Original source drift: " + leaf);
			Path source = work.resolve("sources/" + entry.getKey().replace('.', '/') + ".java");
			Files.createDirectories(source.getParent());
			Files.write(source, bytes);
			sources.add(source);
		}
		Path classes = work.resolve("classes");
		Files.createDirectories(classes);
		String classPath = Path.of(Assert.class.getProtectionDomain().getCodeSource().getLocation().toURI()) +
				File.pathSeparator + Path.of(Nullable.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		try (var files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
			List<String> options = List.of("--release", "17", "-g", "-proc:none", "-classpath", classPath,
					"-d", classes.toString());
			if (!Boolean.TRUE.equals(compiler.getTask(null, files, diagnostics, options, null,
					files.getJavaFileObjectsFromPaths(sources)).call())) {
				throw new IllegalStateException("Original source compilation failed: " + diagnostics.getDiagnostics());
			}
		}
		this.loader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, getClass().getClassLoader()) {
			@Override
			protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				synchronized (getClassLoadingLock(name)) {
					boolean original = SOURCE_HASHES.keySet().stream()
							.anyMatch(owner -> name.equals(owner) || name.startsWith(owner + "$"));
					if (!original) {
						return super.loadClass(name, resolve);
					}
					Class<?> loaded = findLoadedClass(name);
					if (loaded == null) {
						loaded = findClass(name);
					}
					if (resolve) {
						resolveClass(loaded);
					}
					return loaded;
				}
			}
		};
	}


	public Class<?> originalClass(String name) throws ClassNotFoundException {
		return this.loader.loadClass(name);
	}

	public static Object invoke(Method method, Object target, Object... arguments) {
		try {
			method.setAccessible(true);
			return method.invoke(target, arguments);
		}
		catch (InvocationTargetException ex) {
			if (ex.getCause() instanceof RuntimeException runtime) {
				throw runtime;
			}
			if (ex.getCause() instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException(ex.getCause());
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Override
	public void close() throws IOException {
		this.loader.close();
	}

}
