/*******************************************************************************
 * Copyright (c) 2008 Code 9 and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 *   Code 9 - initial API and implementation
 ******************************************************************************/
package org.eclipse.equinox.internal.provisional.p2.directorywatcher;

import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.CompoundQueryable;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.IQueryable;
import org.eclipse.equinox.internal.provisional.spi.p2.artifact.repository.FlatteningIterator;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.query.IQuery;
import org.eclipse.equinox.p2.metadata.query.IQueryResult;
import org.eclipse.equinox.p2.repository.artifact.*;
import org.eclipse.equinox.p2.repository.artifact.spi.ArtifactDescriptor;

public class CachingArtifactRepository implements IArtifactRepository, IFileArtifactRepository {

	private static final String NULL = ""; //$NON-NLS-1$
	private IArtifactRepository innerRepo;
	private Set<IArtifactDescriptor> descriptorsToAdd = new HashSet<IArtifactDescriptor>();
	private Map<IArtifactKey, List<IArtifactDescriptor>> artifactMap = new HashMap<IArtifactKey, List<IArtifactDescriptor>>();
	private Set<IArtifactDescriptor> descriptorsToRemove = new HashSet<IArtifactDescriptor>();
	private Map<String, String> propertyChanges = new HashMap<String, String>();

	protected CachingArtifactRepository(IArtifactRepository innerRepo) {
		this.innerRepo = innerRepo;
	}

	public void save() {
		savePropertyChanges();
		saveAdditions();
		saveRemovals();
	}

	private void saveRemovals() {
		for (IArtifactDescriptor desc : descriptorsToRemove)
			innerRepo.removeDescriptor(desc);
		descriptorsToRemove.clear();
	}

	private void saveAdditions() {
		if (descriptorsToAdd.isEmpty())
			return;
		innerRepo.addDescriptors(descriptorsToAdd.toArray(new IArtifactDescriptor[descriptorsToAdd.size()]));
		descriptorsToAdd.clear();
		artifactMap.clear();
	}

	private void savePropertyChanges() {
		for (String key : propertyChanges.keySet()) {
			String value = propertyChanges.get(key);
			innerRepo.setProperty(key, value == NULL ? null : value);
		}
		propertyChanges.clear();
	}

	private void mapDescriptor(IArtifactDescriptor descriptor) {
		IArtifactKey key = descriptor.getArtifactKey();
		List<IArtifactDescriptor> descriptors = artifactMap.get(key);
		if (descriptors == null) {
			descriptors = new ArrayList<IArtifactDescriptor>();
			artifactMap.put(key, descriptors);
		}
		descriptors.add(descriptor);
	}

	private void unmapDescriptor(IArtifactDescriptor descriptor) {
		IArtifactKey key = descriptor.getArtifactKey();
		List<IArtifactDescriptor> descriptors = artifactMap.get(key);
		if (descriptors == null) {
			// we do not have the descriptor locally so remember it to be removed from
			// the inner repo on save.
			descriptorsToRemove.add(descriptor);
			return;
		}

		descriptors.remove(descriptor);
		if (descriptors.isEmpty())
			artifactMap.remove(key);
	}

	public synchronized void addDescriptors(IArtifactDescriptor[] descriptors) {
		for (int i = 0; i < descriptors.length; i++) {
			((ArtifactDescriptor) descriptors[i]).setRepository(this);
			descriptorsToAdd.add(descriptors[i]);
			mapDescriptor(descriptors[i]);
		}
	}

	public synchronized void addDescriptor(IArtifactDescriptor toAdd) {
		((ArtifactDescriptor) toAdd).setRepository(this);
		descriptorsToAdd.add(toAdd);
		mapDescriptor(toAdd);
	}

	public synchronized IArtifactDescriptor[] getArtifactDescriptors(IArtifactKey key) {
		List<IArtifactDescriptor> result = artifactMap.get(key);
		if (result == null)
			return innerRepo.getArtifactDescriptors(key);
		result = new ArrayList<IArtifactDescriptor>(result);
		result.addAll(Arrays.asList(innerRepo.getArtifactDescriptors(key)));
		return result.toArray(new IArtifactDescriptor[result.size()]);
	}

	public synchronized boolean contains(IArtifactDescriptor descriptor) {
		return descriptorsToAdd.contains(descriptor) || innerRepo.contains(descriptor);
	}

	public synchronized boolean contains(IArtifactKey key) {
		return artifactMap.containsKey(key) || innerRepo.contains(key);
	}

	public IStatus getArtifact(IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor) {
		return innerRepo.getArtifact(descriptor, destination, monitor);
	}

	public IStatus getRawArtifact(IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor) {
		return innerRepo.getRawArtifact(descriptor, destination, monitor);
	}

	public IStatus getArtifacts(IArtifactRequest[] requests, IProgressMonitor monitor) {
		return Status.OK_STATUS;
	}

	public OutputStream getOutputStream(IArtifactDescriptor descriptor) {
		return null;
	}

	public synchronized void removeAll() {
		IArtifactDescriptor[] toRemove = descriptorsToAdd.toArray(new IArtifactDescriptor[descriptorsToAdd.size()]);
		for (int i = 0; i < toRemove.length; i++)
			doRemoveArtifact(toRemove[i]);
	}

	public synchronized void removeDescriptor(IArtifactDescriptor descriptor) {
		doRemoveArtifact(descriptor);
	}

	public synchronized void removeDescriptor(IArtifactKey key) {
		IArtifactDescriptor[] toRemove = getArtifactDescriptors(key);
		for (int i = 0; i < toRemove.length; i++)
			doRemoveArtifact(toRemove[i]);
	}

	/**
	 * Removes the given descriptor and returns <code>true</code> if and only if the
	 * descriptor existed in the repository, and was successfully removed.
	 */
	private boolean doRemoveArtifact(IArtifactDescriptor descriptor) {
		// if the descriptor is already in the pending additoins, remove it
		boolean result = descriptorsToAdd.remove(descriptor);
		if (result)
			unmapDescriptor(descriptor);
		// either way, note this as a descriptor to remove from the inner repo
		descriptorsToRemove.add(descriptor);
		return result;
	}

	public String getDescription() {
		return innerRepo.getDescription();
	}

	public URI getLocation() {
		return innerRepo.getLocation();
	}

	public String getName() {
		return innerRepo.getName();
	}

	public Map<String, String> getProperties() {
		// TODO need to combine the local and inner properties
		return innerRepo.getProperties();
	}

	public String getProvider() {
		return innerRepo.getProvider();
	}

	public String getType() {
		return innerRepo.getType();
	}

	public String getVersion() {
		return innerRepo.getVersion();
	}

	public boolean isModifiable() {
		return innerRepo.isModifiable();
	}

	public void setDescription(String description) {
		innerRepo.setDescription(description);
	}

	public void setName(String name) {
		innerRepo.setName(name);
	}

	public String setProperty(String key, String value) {
		String result = getProperties().get(key);
		propertyChanges.put(key, value == null ? NULL : value);
		return result;
	}

	public void setProvider(String provider) {
		innerRepo.setProvider(provider);
	}

	@SuppressWarnings("rawtypes")
	public Object getAdapter(Class adapter) {
		return innerRepo.getAdapter(adapter);
	}

	public File getArtifactFile(IArtifactKey key) {
		if (innerRepo instanceof IFileArtifactRepository)
			return ((IFileArtifactRepository) innerRepo).getArtifactFile(key);
		return null;
	}

	public File getArtifactFile(IArtifactDescriptor descriptor) {
		if (innerRepo instanceof IFileArtifactRepository)
			return ((IFileArtifactRepository) innerRepo).getArtifactFile(descriptor);
		return null;
	}

	public IArtifactDescriptor createArtifactDescriptor(IArtifactKey key) {
		return innerRepo.createArtifactDescriptor(key);
	}

	public IQueryable<IArtifactDescriptor> descriptorQueryable() {
		final Collection<List<IArtifactDescriptor>> descs = artifactMap.values();
		IQueryable<IArtifactDescriptor> cached = new IQueryable<IArtifactDescriptor>() {
			public IQueryResult<IArtifactDescriptor> query(IQuery<IArtifactDescriptor> query, IProgressMonitor monitor) {
				return query.perform(new FlatteningIterator<IArtifactDescriptor>(descs.iterator()));
			}
		};

		return new CompoundQueryable<IArtifactDescriptor>(cached, innerRepo.descriptorQueryable());
	}

	public IQueryResult<IArtifactKey> query(IQuery<IArtifactKey> query, IProgressMonitor monitor) {
		final Iterator<IArtifactKey> keyIterator = artifactMap.keySet().iterator();
		IQueryable<IArtifactKey> cached = new IQueryable<IArtifactKey>() {
			public IQueryResult<IArtifactKey> query(IQuery<IArtifactKey> q, IProgressMonitor mon) {
				return q.perform(keyIterator);
			}
		};

		CompoundQueryable<IArtifactKey> compound = new CompoundQueryable<IArtifactKey>(cached, innerRepo);
		return compound.query(query, monitor);
	}
}
