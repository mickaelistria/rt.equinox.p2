/*******************************************************************************
 * Copyright (c) 2008, 2011 Code 9 and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 *   Code 9 - initial API and implementation
 *   IBM - ongoing development
 *   SAP AG - ongoing development
 ******************************************************************************/
package org.eclipse.equinox.p2.tests.publisher.actions;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.hasItem;

import java.io.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.core.helpers.FileUtils;
import org.eclipse.equinox.internal.p2.metadata.ArtifactKey;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.IPublisherResult;
import org.eclipse.equinox.p2.publisher.actions.JREAction;
import org.eclipse.equinox.p2.tests.*;
import org.eclipse.equinox.p2.tests.publisher.TestArtifactRepository;
import org.eclipse.equinox.spi.p2.publisher.PublisherHelper;

@SuppressWarnings({"unchecked"})
public class JREActionTest extends ActionTest {

	private File J14 = new File(TestActivator.getTestDataFolder(), "JREActionTest/1.4/"); //$NON-NLS-1$
	private File J15 = new File(TestActivator.getTestDataFolder(), "JREActionTest/1.5/"); //$NON-NLS-1$
	private File J16 = new File(TestActivator.getTestDataFolder(), "JREActionTest/1.6/"); //$NON-NLS-1$
	private File jreWithPackageVersionsFolder = new File(TestActivator.getTestDataFolder(), "JREActionTest/packageVersions/"); //$NON-NLS-1$
	private File jreWithPackageVersionsProfile = new File(TestActivator.getTestDataFolder(), "JREActionTest/packageVersions/test-1.0.0.profile"); //$NON-NLS-1$

	protected TestArtifactRepository artifactRepository = new TestArtifactRepository(getAgent());
	protected TestMetadataRepository metadataRepository;

	public void setUp() throws Exception {
		setupPublisherInfo();
		setupPublisherResult();
	}

	public void test14() throws Exception {
		testAction = new JREAction(J14);
		testAction.perform(publisherInfo, publisherResult, new NullProgressMonitor());
		verifyResults("a.jre.j2se", 92, Version.create("1.4.0"), true); //$NON-NLS-1$
		verifyArtifactRepository(ArtifactKey.parse("binary,a.jre.j2se,1.4.0"), J14, "J2SE-1.4.profile"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public void test15() throws Exception {
		testAction = new JREAction(J15);
		testAction.perform(publisherInfo, publisherResult, new NullProgressMonitor());
		verifyResults("a.jre.j2se", 119, Version.create("1.5.0"), true); //$NON-NLS-1$
		verifyArtifactRepository(ArtifactKey.parse("binary,a.jre.j2se,1.5.0"), J15, "J2SE-1.5.profile"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public void test16() throws Exception {
		testAction = new JREAction(J16);
		testAction.perform(publisherInfo, publisherResult, new NullProgressMonitor());
		verifyResults("a.jre.javase", 117, Version.create("1.6.0"), true); //$NON-NLS-1$
		verifyArtifactRepository(ArtifactKey.parse("binary,a.jre.javase,1.6.0"), J16, "JavaSE-1.6.profile"); //$NON-NLS-1$//$NON-NLS-2$
	}

	public void testOSGiMin() throws Exception {
		testAction = new JREAction("OSGi/Minimum-1.2");
		testAction.perform(publisherInfo, publisherResult, new NullProgressMonitor());
		verifyResults("a.jre.osgi.minimum", 2, Version.create("1.2.0"), false); //$NON-NLS-1$
	}

	public void testPackageVersionsFromJreFolder() throws Exception {
		testAction = new JREAction(jreWithPackageVersionsFolder);
		testAction.perform(publisherInfo, publisherResult, new NullProgressMonitor());

		Collection<IProvidedCapability> providedCapabilities = getPublishedCapabilitiesOf("a.jre.test");
		assertThat(providedCapabilities, hasItem(MetadataFactory.createProvidedCapability(PublisherHelper.CAPABILITY_NS_JAVA_PACKAGE, "my.package", null)));
		assertThat(providedCapabilities, hasItem(MetadataFactory.createProvidedCapability(PublisherHelper.CAPABILITY_NS_JAVA_PACKAGE, "my.package", Version.create("1.0.0"))));

		verifyArtifactRepository(ArtifactKey.parse("binary,a.jre.test,1.0.0"), jreWithPackageVersionsFolder, "test-1.0.0.profile"); //$NON-NLS-1$//$NON-NLS-2$
	}

	public void testPackageVersionsFromJavaProfile() throws Exception {
		// introduced for bug 334519: directly point to a profile file
		testAction = new JREAction(jreWithPackageVersionsProfile);
		testAction.perform(publisherInfo, publisherResult, new NullProgressMonitor());

		Collection<IProvidedCapability> providedCapabilities = getPublishedCapabilitiesOf("a.jre.test");
		assertThat(providedCapabilities, hasItem(MetadataFactory.createProvidedCapability(PublisherHelper.CAPABILITY_NS_JAVA_PACKAGE, "my.package", null)));
		assertThat(providedCapabilities, hasItem(MetadataFactory.createProvidedCapability(PublisherHelper.CAPABILITY_NS_JAVA_PACKAGE, "my.package", Version.create("1.0.0"))));
	}

	public void testDefaultJavaProfile() throws Exception {
		// take note that these constants should be changed each time the default java profile, hardcoded in o.e.e.p2.publisher.actions.JREAction, is changed;
		// this could be avoided by making the respective static properties of JREAction class public but doing so for test purposes only is questionable
		final String DEFAULT_JRE_NAME = "a.jre.javase"; //$NON-NLS-1$
		final Version DEFAULT_JRE_VERSION = Version.parseVersion("1.6"); //$NON-NLS-1$
		final int DEFAULT_NUM_PROVIDED_CAPABILITIES = 159;

		testAction = new JREAction((File) null);
		testAction.perform(publisherInfo, publisherResult, new NullProgressMonitor());
		verifyResults(DEFAULT_JRE_NAME, DEFAULT_NUM_PROVIDED_CAPABILITIES, DEFAULT_JRE_VERSION, false);
	}

	public void testNonExistingJreLocation() {
		File nonExistingProfile = new File(jreWithPackageVersionsFolder, "no.profile");
		testAction = new JREAction(nonExistingProfile);
		try {
			testAction.perform(publisherInfo, publisherResult, new NullProgressMonitor());
			fail("Expected failure when the JRE location does not exists.");
		} catch (IllegalArgumentException e) {
			// test is successful
		} catch (Exception e) {
			fail("Expected IllegalArgumentException when the JRE location does not exists, caught " + e.getClass().getName());
		}
	}

	private void verifyResults(String id, int numProvidedCapabilities, Version JREVersion, boolean testInstructions) {
		ArrayList fooIUs = new ArrayList(publisherResult.getIUs(id, IPublisherResult.ROOT)); //$NON-NLS-1$
		assertTrue(fooIUs.size() == 1);
		IInstallableUnit foo = (IInstallableUnit) fooIUs.get(0);

		// check version
		assertTrue(foo.getVersion().equals(JREVersion));

		// check touchpointType
		assertTrue(foo.getTouchpointType().getId().equalsIgnoreCase("org.eclipse.equinox.p2.native")); //$NON-NLS-1$
		assertTrue(foo.getTouchpointType().getVersion().equals(Version.create("1.0.0"))); //$NON-NLS-1$

		// check provided capabilities
		Collection<IProvidedCapability> fooProvidedCapabilities = foo.getProvidedCapabilities();
		assertTrue(fooProvidedCapabilities.size() == numProvidedCapabilities);

		ArrayList barIUs = new ArrayList(publisherResult.getIUs("config." + id, IPublisherResult.ROOT)); //$NON-NLS-1$
		assertTrue(barIUs.size() == 1);
		IInstallableUnit bar = (IInstallableUnit) barIUs.get(0);

		if (testInstructions) {
			Map instructions = bar.getTouchpointData().iterator().next().getInstructions();
			assertTrue(((ITouchpointInstruction) instructions.get("install")).getBody().equals("unzip(source:@artifact, target:${installFolder});")); //$NON-NLS-1$//$NON-NLS-2$
			assertTrue(((ITouchpointInstruction) instructions.get("uninstall")).getBody().equals("cleanupzip(source:@artifact, target:${installFolder});")); //$NON-NLS-1$ //$NON-NLS-2$
		}
		assertTrue(bar instanceof IInstallableUnitFragment);
		Collection<IRequirement> requiredCapability = ((IInstallableUnitFragment) bar).getHost();
		verifyRequiredCapability(requiredCapability, IInstallableUnit.NAMESPACE_IU_ID, id, new VersionRange(JREVersion, true, Version.MAX_VERSION, true)); //$NON-NLS-1$ 
		assertTrue(requiredCapability.size() == 1);

		Collection<IProvidedCapability> providedCapability = bar.getProvidedCapabilities();
		verifyProvidedCapability(providedCapability, IInstallableUnit.NAMESPACE_IU_ID, "config." + id, JREVersion); //$NON-NLS-1$ 
		assertTrue(providedCapability.size() == 1);

		assertTrue(bar.getProperty("org.eclipse.equinox.p2.type.fragment").equals("true")); //$NON-NLS-1$//$NON-NLS-2$
		assertTrue(bar.getVersion().equals(JREVersion));
	}

	private void verifyArtifactRepository(IArtifactKey key, File JRELocation, final String fileName) throws IOException {
		assertTrue(artifactRepository.contains(key));
		ByteArrayOutputStream content = new ByteArrayOutputStream();
		FileFilter fileFilter = new FileFilter() {
			public boolean accept(File file) {
				return file.getName().endsWith(fileName);
			}
		};
		File[] contentBytes = JRELocation.listFiles(fileFilter);
		FileUtils.copyStream(new FileInputStream(contentBytes[0]), false, content, true);
		ZipInputStream zipInputStream = artifactRepository.getZipInputStream(key);

		Map fileMap = new HashMap();
		fileMap.put(fileName, new Object[] {contentBytes[0], content.toByteArray()});
		TestData.assertContains(fileMap, zipInputStream, true);
	}

	private Collection<IProvidedCapability> getPublishedCapabilitiesOf(String id) {
		Collection<IInstallableUnit> ius = publisherResult.getIUs(id, IPublisherResult.ROOT);
		assertTrue(ius.size() == 1);
		IInstallableUnit iu = ius.iterator().next();
		return iu.getProvidedCapabilities();
	}

	protected void insertPublisherInfoBehavior() {
		expect(publisherInfo.getArtifactRepository()).andReturn(artifactRepository).anyTimes();
		expect(publisherInfo.getArtifactOptions()).andReturn(IPublisherInfo.A_PUBLISH).anyTimes();
	}

}
