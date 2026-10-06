package com.kasirkita.pos.data.model

import com.google.gson.Gson
import com.kasirkita.pos.domain.model.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierGroupResponseContractTest {
    private val gson = Gson()

    @Test
    fun globalCreateResponse_withoutAssignmentMetadata_mapsSuccessfully() {
        val response = gson.fromJson(globalGroupJson("Extra", "group-extra"), ModifierGroupResponse::class.java)

        val group = response.toDomain()

        assertEquals("group-extra", group.id)
        assertEquals("tenant-id", group.tenantId)
        assertEquals("Extra", group.name)
        assertTrue(group.isActive)
        assertEquals(1, group.displayOrder)
        assertFalse(group.required)
        assertEquals(SelectionMode.SINGLE, group.selectionType)
    }

    @Test
    fun globalListResponse_withIceHotAndExtra_mapsSuccessfully() {
        val response = gson.fromJson(
            "[${globalGroupJson("ICE/HOT", "group-ice")},${globalGroupJson("Extra", "group-extra")}]",
            Array<ModifierGroupResponse>::class.java,
        )

        val groups = response.map { it.toDomain() }

        assertEquals(listOf("ICE/HOT", "Extra"), groups.map { it.name })
        assertEquals(listOf(1, 1), groups.map { it.displayOrder })
    }

    @Test
    fun productModifierResponse_preservesAssignmentMetadata() {
        val response = gson.fromJson(
            """
            {
              "id": "group-size",
              "name": "Ukuran",
              "required": true,
              "selection_type": "SINGLE",
              "display_order": 2,
              "options": []
            }
            """.trimIndent(),
            ProductModifierGroupResponse::class.java,
        )

        val group = response.toDomain("tenant-id")

        assertTrue(group.required)
        assertEquals(SelectionMode.SINGLE, group.selectionType)
        assertEquals(2, group.displayOrder)
        assertTrue(group.isActive)
    }

    private fun globalGroupJson(name: String, id: String): String =
        """
        {
          "id": "$id",
          "tenant_id": "tenant-id",
          "name": "$name",
          "is_active": true,
          "display_order": 1,
          "options": []
        }
        """.trimIndent()
}
