/*******************************************************************************
 * Copyright (c) 2009-2013 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:

 *   * Paul Klint - Paul.Klint@cwi.nl - CWI
 *   * Davy Landman -davy.landman@gmail.com - CWI
*******************************************************************************/

package org.rascalmpl.uri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.eclipse.imp.pdb.facts.ISourceLocation;

/**
 * This resolver is used for example for the scheme "test-modules", which amongst others 
 * generates modules during tests.
 * These modules are implemented via an in-memory "file system" that guarantees
 * that "lastModified" is monotone increasing, i.e. after a write to a file lastModified
 * is ALWAYS larger than for the previous version of the same file.
 * When files are written at high speeed (e.g. with 10-30 ms intervals), this property is, 
 * unfortunately, not guaranteed on all operating systems.
 * 
 * So if you are writing temporary files very frequently and use lastModified to mark the fields 
 * as dirty, use an instance of this resolver to guarantee the dirty marking.
 * 
 * The locations should not use the autority field, as that is ignored.
 * 
 * BE AWARE that the information in this in-memory file system is volatile and does not survive:
 * - program execution
 * - replacement by another in-memory filesystem for the same scheme
 *
 */

public class InMemoryResolver implements ISourceLocationInputOutput {
	
    private final String scheme;
    
    private final JarTreeHierachy fileSystem = new JarTreeHierachy() { };

	public InMemoryResolver(String scheme) {
		//System.err.println("CREATE InMemoryResolver: " + scheme + ": " + this);
        this.scheme = scheme;
    }

    @Override
	public String scheme() {
		return scheme;
	}
	
	private static final class File extends JarTreeHierachy.FSEntry {
		byte[] contents;
		public File() {
		    super(System.currentTimeMillis());
			contents = null;
		}
		public void newContent(byte[] byteArray) {
			long newTimestamp = System.currentTimeMillis();
			if (newTimestamp <= lastModified) {
				newTimestamp =  lastModified + 1;
			}
			lastModified = newTimestamp;
			contents = byteArray;
		}
		public String toString(){
		    return String.valueOf(lastModified) ;//+ ":\n" +new String(contents, StandardCharsets.UTF_8);
		}
	}

	private File get(ISourceLocation uri) {
	    return (File)fileSystem.fs.get(uri.getPath());
	}
	
	@Override
	public InputStream getInputStream(ISourceLocation uri)
			throws IOException {
		File file = get(uri);
		if (file == null) {
			System.err.println(this + " getInputStream: null");
			throw new IOException();
		}
		//System.err.println(this + " getInputStream: " + uri + "?" + file.lastModified);
		return new ByteArrayInputStream(file.contents);
	}

	@Override
	public OutputStream getOutputStream(ISourceLocation uri, boolean append)
			throws IOException {
		//System.err.println(this + " getOutputStream " + uri);
		return new ByteArrayOutputStream() {
			@Override
			public void close() throws IOException {
				super.close();
				File file = get(uri);
				if (file == null) {
				    file = new File();
				    fileSystem.fs.put(uri.getPath(), file);
				}
				file.newContent(this.toByteArray());
				//System.err.println(this + " getOutputStream.close " + uri + "?" + file.lastModified);
			}
		};
	}
	
	@Override
	public long lastModified(ISourceLocation uri) throws IOException {
		File file = get(uri);
		if (file == null) {
			throw new IOException();
		}
		return file.lastModified;
	}
	
	@Override
	public Charset getCharset(ISourceLocation uri) throws IOException {
		return null;
	}

	@Override
	public boolean exists(ISourceLocation uri) {
		return fileSystem.exists(uri.getPath());
	}

	@Override
	public boolean isDirectory(ISourceLocation uri) {
	    return fileSystem.isDirectory(uri.getPath());
	}

	@Override
	public boolean isFile(ISourceLocation uri) {
	    return fileSystem.isFile(uri.getPath());
	}

	@Override
	public String[] list(ISourceLocation uri) throws IOException {
	    return fileSystem.directChildren(uri.getPath());
	}

	@Override
	public boolean supportsHost() {
		return false;
	}

	@Override
	public void mkDirectory(ISourceLocation uri) throws IOException {
	}

	@Override
	public void remove(ISourceLocation uri) throws IOException {
	    fileSystem.fs.remove(uri.getPath());
	}
}