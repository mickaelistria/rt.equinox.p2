/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Code 9 - ongoing development
 *******************************************************************************/
package org.eclipse.equinox.p2.tests.metadata.repository;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.*;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.equinox.internal.provisional.p2.core.Version;
import org.eclipse.equinox.internal.provisional.p2.core.eventbus.ProvisioningListener;
import org.eclipse.equinox.internal.provisional.p2.core.eventbus.SynchronousProvisioningListener;
import org.eclipse.equinox.internal.provisional.p2.core.repository.IRepository;
import org.eclipse.equinox.internal.provisional.p2.core.repository.RepositoryEvent;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.metadata.MetadataFactory;
import org.eclipse.equinox.internal.provisional.p2.metadata.MetadataFactory.InstallableUnitDescription;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.InstallableUnitQuery;
import org.eclipse.equinox.internal.provisional.p2.metadata.repository.IMetadataRepository;
import org.eclipse.equinox.internal.provisional.p2.metadata.repository.IMetadataRepositoryManager;
import org.eclipse.equinox.internal.provisional.p2.query.Collector;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

/**
 * Test API of the local metadata repository implementation.
 */
public class LocalMetadataRepositoryTest extends AbstractProvisioningTest {
	private static final String TEST_KEY = "TestKey";
	private static final String TEST_VALUE = "TestValue";
	protected File repoLocation;

	protected void setUp() throws Exception {
		super.setUp();
		String tempDir = System.getProperty("java.io.tmpdir");
		repoLocation = new File(tempDir, "LocalMetadataRepositoryTest");
		AbstractProvisioningTest.delete(repoLocation);
		repoLocation.mkdir();
	}

	protected void tearDown() throws Exception {
		getMetadataRepositoryManager().removeRepository(repoLocation.toURI());
		delete(repoLocation);
		super.tearDown();
	}

	public void testCompressedRepository() throws MalformedURLException, ProvisionException {
		IMetadataRepositoryManager manager = getMetadataRepositoryManager();
		Map properties = new HashMap();
		properties.put(IRepository.PROP_COMPRESSED, "true");
		IMetadataRepository repo = manager.createRepository(repoLocation.toURI(), "TestRepo", IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY, properties);

		InstallableUnitDescription descriptor = new MetadataFactory.InstallableUnitDescription();
		descriptor.setId("testIuId");
		descriptor.setVersion(new Version("3.2.1"));
		IInstallableUnit iu = MetadataFactory.createInstallableUnit(descriptor);
		repo.addInstallableUnits(new IInstallableUnit[] {iu});

		File[] files = repoLocation.listFiles();
		boolean jarFilePresent = false;
		boolean xmlFilePresent = false;
		// one of the files in the repository should be the content.xml.jar
		for (int i = 0; i < files.length; i++) {
			if ("content.jar".equalsIgnoreCase(files[i].getName())) {
				jarFilePresent = true;
			}
			if ("content.xml".equalsIgnoreCase(files[i].getName())) {
				xmlFilePresent = true;
			}
		}
		if (!jarFilePresent) {
			fail("Repository did not create JAR for content.xml");
		}
		if (xmlFilePresent) {
			fail("Repository should not create content.xml");
		}
	}

	public void testGetProperties() throws MalformedURLException, ProvisionException {
		IMetadataRepositoryManager manager = getMetadataRepositoryManager();
		IMetadataRepository repo = manager.createRepository(repoLocation.toURI(), "TestRepo", IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY, null);
		Map properties = repo.getProperties();
		//attempting to modify the properties should fail
		try {
			properties.put(TEST_KEY, TEST_VALUE);
			fail("Should not allow setting property");
		} catch (RuntimeException e) {
			//expected
		}
	}

	public void testSetProperty() throws MalformedURLException, ProvisionException {
		IMetadataRepositoryManager manager = getMetadataRepositoryManager();
		IMetadataRepository repo = manager.createRepository(repoLocation.toURI(), "TestRepo", IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY, null);
		Map properties = repo.getProperties();
		assertTrue("1.0", !properties.containsKey(TEST_KEY));
		repo.setProperty(TEST_KEY, TEST_VALUE);

		//the previously obtained properties should not be affected by subsequent changes
		assertTrue("1.1", !properties.containsKey(TEST_KEY));
		properties = repo.getProperties();
		assertTrue("1.2", properties.containsKey(TEST_KEY));

		//going back to repo manager, should still get the new property
		repo = manager.loadRepository(repoLocation.toURI(), null);
		properties = repo.getProperties();
		assertTrue("1.3", properties.containsKey(TEST_KEY));

		//setting a null value should remove the key
		repo.setProperty(TEST_KEY, null);
		properties = repo.getProperties();
		assertTrue("1.4", !properties.containsKey(TEST_KEY));
	}

	public void testAddRemoveIUs() throws ProvisionException {
		IMetadataRepositoryManager manager = getMetadataRepositoryManager();
		IMetadataRepository repo = manager.createRepository(repoLocation.toURI(), "TestRepo", IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY, null);
		IInstallableUnit iu = createIU("foo");
		repo.addInstallableUnits(new IInstallableUnit[] {iu});
		Collector result = repo.query(new InstallableUnitQuery(null), new Collector(), getMonitor());
		assertTrue("1.0", result.size() == 1);
		repo.removeAll();
		result = repo.query(new InstallableUnitQuery(null), new Collector(), getMonitor());
		assertTrue("1.1", result.isEmpty());
	}

	public void testRemoveByQuery() throws ProvisionException {
		IMetadataRepositoryManager manager = getMetadataRepositoryManager();
		IMetadataRepository repo = manager.createRepository(repoLocation.toURI(), "TestRepo", IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY, null);
		IInstallableUnit iu = createIU("foo");
		IInstallableUnit iu2 = createIU("bar");
		repo.addInstallableUnits(new IInstallableUnit[] {iu, iu2});
		Collector result = repo.query(new InstallableUnitQuery(null), new Collector(), getMonitor());
		assertTrue("1.0", result.size() == 2);
		repo.removeInstallableUnits(new InstallableUnitQuery("foo"), getMonitor());
		result = repo.query(new InstallableUnitQuery(null), new Collector(), getMonitor());
		assertTrue("1.1", result.size() == 1);
		repo.removeInstallableUnits(new InstallableUnitQuery("bar"), getMonitor());
		result = repo.query(new InstallableUnitQuery(null), new Collector(), getMonitor());
		assertTrue("1.2", result.isEmpty());

	}

	public void testUncompressedRepository() throws MalformedURLException, ProvisionException {
		IMetadataRepositoryManager manager = getMetadataRepositoryManager();
		Map properties = new HashMap();
		properties.put(IRepository.PROP_COMPRESSED, "false");
		IMetadataRepository repo = manager.createRepository(repoLocation.toURI(), "TestRepo", IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY, properties);

		InstallableUnitDescription descriptor = new MetadataFactory.InstallableUnitDescription();
		descriptor.setId("testIuId");
		descriptor.setVersion(new Version("3.2.1"));
		IInstallableUnit iu = MetadataFactory.createInstallableUnit(descriptor);
		repo.addInstallableUnits(new IInstallableUnit[] {iu});

		File[] files = repoLocation.listFiles();
		boolean jarFilePresent = false;
		// none of the files in the repository should be the content.xml.jar
		for (int i = 0; i < files.length; i++) {
			if ("content.jar".equalsIgnoreCase(files[i].getName())) {
				jarFilePresent = true;
			}
		}
		if (jarFilePresent) {
			fail("Repository should not create JAR for content.xml");
		}
	}

	/**
	 * Tests loading a repository that has a reference to itself as a disabled repository.
	 * @throws MalformedURLException 
	 * @throws ProvisionException 
	 */
	public void testLoadSelfReference() throws MalformedURLException, ProvisionException {
		//setup a repository that has a reference to itself in disabled state
		IMetadataRepositoryManager manager = getMetadataRepositoryManager();
		Map properties = new HashMap();
		properties.put(IRepository.PROP_COMPRESSED, "false");
		final URI repoURI = repoLocation.toURI();
		IMetadataRepository repo = manager.createRepository(repoURI, "testLoadSelfReference", IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY, properties);
		repo.addReference(repoURI, IRepository.TYPE_METADATA, IRepository.NONE);
		//adding a reference doesn't save the repository, but setting a property does
		repo.setProperty("changed", "false");

		final int[] callCount = new int[] {0};
		final boolean[] wasEnabled = new boolean[] {false};
		//add a listener to ensure we receive add events with the repository enabled
		ProvisioningListener listener = new SynchronousProvisioningListener() {
			public void notify(EventObject o) {
				if (!(o instanceof RepositoryEvent))
					return;
				RepositoryEvent event = (RepositoryEvent) o;
				if (event.getKind() != RepositoryEvent.ADDED)
					return;
				if (!event.getRepositoryLocation().equals(repoURI))
					return;
				wasEnabled[0] = event.isRepositoryEnabled();
				callCount[0]++;
			}
		};
		getEventBus().addListener(listener);
		try {
			//now remove and reload the repository
			manager.removeRepository(repoURI);
			repo = manager.loadRepository(repoURI, null);
			assertTrue("1.0", manager.isEnabled(repoURI));
			assertTrue("1.1", wasEnabled[0]);
			assertEquals("1.2", 1, callCount[0]);
		} finally {
			getEventBus().removeListener(listener);
		}
	}

	public void testRefreshSelfReference() throws ProvisionException {
		//setup a repository that has a reference to itself in disabled state
		IMetadataRepositoryManager manager = getMetadataRepositoryManager();
		Map properties = new HashMap();
		properties.put(IRepository.PROP_COMPRESSED, "false");
		final URI repoURL = repoLocation.toURI();
		IMetadataRepository repo = manager.createRepository(repoURL, "testRefreshSelfReference", IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY, properties);
		repo.addReference(repoURL, IRepository.TYPE_METADATA, IRepository.NONE);
		//adding a reference doesn't save the repository, but setting a property does
		repo.setProperty("changed", "false");

		final int[] callCount = new int[] {0};
		final boolean[] wasEnabled = new boolean[] {false};
		//add a listener to ensure we receive add events with the repository enabled
		ProvisioningListener listener = new SynchronousProvisioningListener() {
			public void notify(EventObject o) {
				if (!(o instanceof RepositoryEvent))
					return;
				RepositoryEvent event = (RepositoryEvent) o;
				if (event.getKind() != RepositoryEvent.ADDED)
					return;
				if (!event.getRepositoryLocation().equals(repoURL))
					return;
				wasEnabled[0] = event.isRepositoryEnabled();
				callCount[0]++;
			}
		};
		getEventBus().addListener(listener);
		try {
			//ensure refreshing the repository doesn't disable it
			manager.refreshRepository(repoURL, null);
			assertTrue("1.0", manager.isEnabled(repoURL));
			assertTrue("1.1", wasEnabled[0]);
			assertEquals("1.2", 1, callCount[0]);
		} finally {
			getEventBus().removeListener(listener);
		}
	}
}
