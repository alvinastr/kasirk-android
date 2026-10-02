package com.kasirkita.pos.presentation.authv2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kasirkita.pos.domain.model.ResolvedStore
import com.kasirkita.pos.domain.model.StoreUser
import com.kasirkita.pos.domain.model.UserRole
import com.kasirkita.pos.ui.components.KasirCard
import com.kasirkita.pos.ui.components.KasirEmptyState
import com.kasirkita.pos.ui.components.KasirSecondaryButton
import com.kasirkita.pos.ui.components.KasirTextButton
import com.kasirkita.pos.ui.theme.KasirSpacing

@Composable
fun UserSelectionScreen(
    store: ResolvedStore,
    onUserSelected: (StoreUser) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = KasirSpacing.ScreenPadding, vertical = 16.dp),
    ) {
        KasirTextButton(
            text = "Kembali",
            onClick = onBack,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Pilih pengguna",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = store.tenant.name,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (store.users.isEmpty()) {
            KasirEmptyState(
                message = "Belum ada pengguna aktif untuk toko ini.",
                modifier = Modifier.weight(1f),
            )
            KasirSecondaryButton(text = "Periksa kode toko", onClick = onBack, modifier = Modifier.fillMaxWidth())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(KasirSpacing.ItemGap),
            ) {
                items(
                    items = store.users,
                    key = StoreUser::id,
                ) { user ->
                    UserCard(
                        user = user,
                        onClick = { onUserSelected(user) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserCard(
    user: StoreUser,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KasirCard(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = user.role.displayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            KasirSecondaryButton(
                text = "Pilih",
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal fun UserRole.displayName(): String = when (this) {
    UserRole.OWNER -> "Pemilik"
    UserRole.ADMIN -> "Admin"
    UserRole.CASHIER -> "Kasir"
}
