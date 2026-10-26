/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * The S3 blob store. The BlobStore itself and its auto-configuration land with the AWS SDK; the
 * layout, settings and redirect rules are fixed here first.
 */
@NullMarked
package com.bulmerkiln.storage.s3;

import org.jspecify.annotations.NullMarked;
