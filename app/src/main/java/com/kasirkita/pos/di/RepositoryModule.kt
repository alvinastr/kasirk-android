package com.kasirkita.pos.di

import com.kasirkita.pos.data.repository.AuthV2RepositoryImpl
import com.kasirkita.pos.data.repository.CartRepositoryImpl
import com.kasirkita.pos.data.repository.CategoryRepositoryImpl
import com.kasirkita.pos.data.repository.ModifierRepositoryImpl
import com.kasirkita.pos.data.repository.OutletRepositoryImpl
import com.kasirkita.pos.data.repository.OfflineSyncRepositoryImpl
import com.kasirkita.pos.data.repository.ProductRepositoryImpl
import com.kasirkita.pos.data.repository.ReportRepositoryImpl
import com.kasirkita.pos.data.repository.HeldOrderRepositoryImpl
import com.kasirkita.pos.data.repository.ReceiptRepositoryImpl
import com.kasirkita.pos.data.repository.ShiftRepositoryImpl
import com.kasirkita.pos.data.repository.StockRepositoryImpl
import com.kasirkita.pos.data.repository.TransactionRepositoryImpl
import com.kasirkita.pos.domain.repository.HeldOrderRepository
import com.kasirkita.pos.domain.repository.AuthV2Repository
import com.kasirkita.pos.domain.repository.CartRepository
import com.kasirkita.pos.domain.repository.CategoryRepository
import com.kasirkita.pos.domain.repository.ModifierRepository
import com.kasirkita.pos.domain.repository.OutletRepository
import com.kasirkita.pos.domain.repository.OfflineSyncRepository
import com.kasirkita.pos.domain.repository.ProductRepository
import com.kasirkita.pos.domain.repository.ReportRepository
import com.kasirkita.pos.domain.repository.ReceiptRepository
import com.kasirkita.pos.domain.repository.ShiftRepository
import com.kasirkita.pos.domain.repository.StockRepository
import com.kasirkita.pos.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthV2Repository(
        implementation: AuthV2RepositoryImpl,
    ): AuthV2Repository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(
        implementation: CategoryRepositoryImpl,
    ): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindModifierRepository(
        implementation: ModifierRepositoryImpl,
    ): ModifierRepository

    @Binds
    @Singleton
    abstract fun bindProductRepository(
        implementation: ProductRepositoryImpl,
    ): ProductRepository

    @Binds
    @Singleton
    abstract fun bindReportRepository(
        implementation: ReportRepositoryImpl,
    ): ReportRepository

    @Binds
    @Singleton
    abstract fun bindReceiptRepository(
        implementation: ReceiptRepositoryImpl,
    ): ReceiptRepository

    @Binds
    @Singleton
    abstract fun bindCartRepository(
        implementation: CartRepositoryImpl,
    ): CartRepository

    @Binds
    @Singleton
    abstract fun bindOutletRepository(
        implementation: OutletRepositoryImpl,
    ): OutletRepository

    @Binds
    @Singleton
    abstract fun bindOfflineSyncRepository(
        implementation: OfflineSyncRepositoryImpl,
    ): OfflineSyncRepository

    @Binds
    @Singleton
    abstract fun bindShiftRepository(
        implementation: ShiftRepositoryImpl,
    ): ShiftRepository

    @Binds
    @Singleton
    abstract fun bindStockRepository(
        implementation: StockRepositoryImpl,
    ): StockRepository

    @Binds
    @Singleton
    abstract fun bindHeldOrderRepository(
        implementation: HeldOrderRepositoryImpl,
    ): HeldOrderRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        implementation: TransactionRepositoryImpl,
    ): TransactionRepository
}
