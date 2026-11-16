package com.example.myproducts.di

import com.example.myproducts.data.ProductRepositoryImpl
import com.example.myproducts.domain.ProductRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The one line that decides which ProductRepository the app uses. ViewModels ask for the
 * interface; tests hand them a fake instead.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository
}
