/******************************************************************************* 
* Copyright (c) 2009, 2010 EclipseSource and others. All rights reserved. This
* program and the accompanying materials are made available under the terms of
* the Eclipse Public License v1.0 which accompanies this distribution, and is
* available at http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*   EclipseSource - initial API and implementation
******************************************************************************/
package org.eclipse.equinox.p2.tests.publisher.actions;

import java.io.*;
import java.net.URI;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.internal.p2.updatesite.CategoryPublisherApplication;
import org.eclipse.equinox.p2.publisher.AbstractPublisherApplication;
import org.eclipse.equinox.p2.tests.*;
import org.osgi.framework.Bundle;

/**
 *
 */
public class CategoryPublisherTest extends AbstractProvisioningTest {

	/**
	 * runs default director app.
	 */
	protected StringBuffer runPublisherApp(AbstractPublisherApplication application, final String[] args) throws Exception {
		PrintStream out = System.out;
		StringBuffer buffer = new StringBuffer();
		try {
			System.setOut(new PrintStream(new StringBufferStream(buffer)));
			application.run(args);
		} finally {
			System.setOut(out);
		}
		return buffer;
	}

	public void testCompressCategoryRepo() throws Exception {
		File repository = null;
		try {
			repository = getTempFolder();
			URI repositoryURI = repository.toURI();
			URI category1 = TestData.getFile("CategoryPublisherTests", "category1.xml").toURI();
			String[] firstRun = new String[] {"-metadataRepository", repositoryURI.toString(), "-categoryDefinition", category1.toString(), "-categoryQualifier", "foo"};
			String[] secondRun = new String[] {"-metadataRepository", repositoryURI.toString(), "-categoryDefinition", category1.toString(), "-categoryQualifier", "foo", "-compress"};

			CategoryPublisherApplication categoryPublisherApplication = new CategoryPublisherApplication();
			runPublisherApp(categoryPublisherApplication, firstRun);

			categoryPublisherApplication = new CategoryPublisherApplication();
			runPublisherApp(categoryPublisherApplication, secondRun);

			assertContains(repository, "content.jar");

		} finally {
			if (repository != null & repository.exists())
				delete(repository);
		}
	}

	public void testRememberRepoShapeJar() throws Exception {
		File repository = null;
		try {
			repository = getTempFolder();
			URI repositoryURI = repository.toURI();
			URI category1 = TestData.getFile("CategoryPublisherTests", "category1.xml").toURI();
			String[] firstRun = new String[] {"-metadataRepository", repositoryURI.toString(), "-categoryDefinition", category1.toString(), "-categoryQualifier", "foo", "-compress"};
			String[] secondRun = new String[] {"-metadataRepository", repositoryURI.toString(), "-categoryDefinition", category1.toString(), "-categoryQualifier", "foo"};

			CategoryPublisherApplication categoryPublisherApplication = new CategoryPublisherApplication();
			runPublisherApp(categoryPublisherApplication, firstRun);

			categoryPublisherApplication = new CategoryPublisherApplication();
			runPublisherApp(categoryPublisherApplication, secondRun);

			assertContains(repository, "content.jar");

		} finally {
			if (repository != null & repository.exists())
				delete(repository);
		}
	}

	public void testRememberRepoShapeXML() throws Exception {
		File repository = null;
		try {
			repository = getTempFolder();
			URI repositoryURI = repository.toURI();
			URI category1 = TestData.getFile("CategoryPublisherTests", "category1.xml").toURI();
			String[] firstRun = new String[] {"-metadataRepository", repositoryURI.toString(), "-categoryDefinition", category1.toString(), "-categoryQualifier", "foo"};
			String[] secondRun = new String[] {"-metadataRepository", repositoryURI.toString(), "-categoryDefinition", category1.toString(), "-categoryQualifier", "foo"};

			CategoryPublisherApplication categoryPublisherApplication = new CategoryPublisherApplication();
			runPublisherApp(categoryPublisherApplication, firstRun);

			categoryPublisherApplication = new CategoryPublisherApplication();
			runPublisherApp(categoryPublisherApplication, secondRun);

			assertContains(repository, "content.xml");

		} finally {
			if (repository != null & repository.exists())
				delete(repository);
		}
	}

	public void testRepoWithBundle() throws Exception {
		File repository = null;
		File categoryFolder = null;
		BufferedReader contentReader = null;
		try {
			categoryFolder = getTempFolder();
			File categoryFile = new File(categoryFolder, "category.xml");
			categoryFile.createNewFile();
			FileOutputStream categoryStream = new FileOutputStream(categoryFile);
			categoryStream.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<site>\n".getBytes());

			Bundle bundle = Platform.getBundle("org.eclipse.core.runtime");
			categoryStream.write(("<bundle url=\"plugins/" + bundle.getSymbolicName() + "_" + bundle.getVersion().toString() + ".jar\" id=\"" + bundle.getSymbolicName() + "\" version=\"" + bundle.getVersion().toString() + "\"/>\n").getBytes());
			categoryStream.write("</site>".getBytes());
			categoryStream.close();

			repository = getTempFolder();
			URI repositoryURI = repository.toURI();
			String[] args = new String[] {"-metadataRepository", repositoryURI.toString(), "-categoryDefinition", categoryFile.toURI().toString()};

			CategoryPublisherApplication categoryPublisherApplication = new CategoryPublisherApplication();
			runPublisherApp(categoryPublisherApplication, args);

			File contentFile = new File(repository, "content.xml");
			contentReader = new BufferedReader(new FileReader(contentFile));
			String line = null;
			boolean found = false;
			while (!found && (line = contentReader.readLine()) != null) {
				if (line.contains("org.eclipse.core.runtime")) {
					found = true;
				}
			}
			assertTrue("Bundle IU not found in output repo", found);

		} finally {
			if (repository != null && repository.exists())
				delete(repository);
			if (categoryFolder != null && categoryFolder.exists()) {
				delete(categoryFolder);
			}
			if (contentReader != null) {
				contentReader.close();
			}
		}
	}

	public void assertContains(File directory, String file) {
		assertNotNull(directory);
		assertTrue(directory.exists());
		assertTrue(directory.isDirectory());
		File[] listFiles = directory.listFiles();
		for (int i = 0; i < listFiles.length; i++) {
			if (listFiles[i].getName().equals(file))
				return;
		}
		fail("Directory does not contain file: " + file);
	}
}
