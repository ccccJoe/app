/*
 * File: RepositoryModule.kt
 * Description: Hilt module placeholder. All repositories and use cases use @Inject constructors, so explicit @Provides bindings are unnecessary.
 * Author: SIMS Team
 */
package com.simsapp.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * RepositoryModule
 *
 * Empty module by design. We rely on @Inject constructors for concrete classes
 * (e.g., repositories, use cases, schedulers) to avoid redundant bindings and
 * potential circular dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    // Intentionally left empty.
}