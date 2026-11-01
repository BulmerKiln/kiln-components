/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.domain;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.digest.InvalidDigestException;
import com.bulmerkiln.format.oci.constant.OciMediaTypes;
import com.bulmerkiln.format.oci.dto.OciDescriptorRequest;
import com.bulmerkiln.format.oci.dto.OciManifestRequest;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The descriptors a manifest names: an image's config and layers, an index's manifests, its subject. */
final class OciDescriptorReader {

    private OciDescriptorReader() {}

    /** The config and layers of an image manifest; none for an index. Refuses other media types. */
    static List<OciDescriptor> blobs(OciManifestRequest json, String mediaType) {
        if (OciMediaTypes.INDEXES.contains(mediaType)) {
            if (json.getManifests() == null || json.getConfig() != null || json.getLayers() != null) {
                throw OciManifestParser.invalid("An index has manifests and no config or layers");
            }
            return List.of();
        }
        if (!OciMediaTypes.IMAGE_MANIFESTS.contains(mediaType)) {
            throw OciManifestParser.invalid("Unsupported manifest media type");
        }
        OciDescriptorRequest config = json.getConfig();
        if (config == null || json.getManifests() != null) {
            throw OciManifestParser.invalid("An image manifest has a config and no manifests");
        }
        List<OciDescriptor> blobs = new ArrayList<>();
        blobs.add(descriptor(config));
        for (OciDescriptorRequest layer : orEmpty(json.getLayers())) {
            blobs.add(descriptor(layer));
        }
        return List.copyOf(blobs);
    }

    /** The child manifests of an index; none for an image manifest. */
    static List<OciDescriptor> manifests(OciManifestRequest json, String mediaType) {
        if (!OciMediaTypes.INDEXES.contains(mediaType)) {
            return List.of();
        }
        return orEmpty(json.getManifests()).stream()
                .map(OciDescriptorReader::descriptor)
                .toList();
    }

    static OciDescriptor descriptor(@Nullable OciDescriptorRequest json) {
        if (json == null) {
            throw OciManifestParser.invalid("A descriptor needs a mediaType, digest and size");
        }
        String mediaType = json.getMediaType();
        String digest = json.getDigest();
        Long size = json.getSize();
        if (mediaType == null || digest == null || size == null) {
            throw OciManifestParser.invalid("A descriptor needs a mediaType, digest and size");
        }
        if (size < 0) {
            throw OciManifestParser.invalid("A descriptor size must not be negative");
        }
        try {
            return new OciDescriptor(mediaType, Digest.parse(digest), size, json.getArtifactType());
        } catch (InvalidDigestException e) {
            throw OciManifestParser.invalid("A descriptor has an invalid digest");
        }
    }

    private static <T> List<T> orEmpty(@Nullable List<T> list) {
        return list == null ? List.of() : list;
    }
}
