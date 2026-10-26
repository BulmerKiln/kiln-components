package com.bulmerkiln.build

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Limits how many modules PIT mutates at once. Each run starts a JVM per thread, so unbounded
 * parallel modules multiply them and can exhaust the machine's memory.
 */
abstract class PitestLimit : BuildService<BuildServiceParameters.None>
