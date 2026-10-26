/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * The format subsystem. Each registry format (OCI, npm, Maven, Cargo, and any added later) is a
 * module that implements {@link com.bulmerkiln.format.FormatModule} and passes the
 * {@code FormatModuleContract} suite from this module's test fixtures. The rules that must hold in
 * every format live here and are enforced by type: a refusal's HTTP status comes from its
 * {@link com.bulmerkiln.format.Reason}, so no format can answer a blocked read with {@code 404}
 * or a client error with a {@code 5xx}.
 *
 * <p>Formats reach other subsystems only through the ports defined here, such as
 * {@link com.bulmerkiln.format.ArtifactPolicy}, never through those subsystems' own modules.
 */
@NullMarked
package com.bulmerkiln.format;

import org.jspecify.annotations.NullMarked;
