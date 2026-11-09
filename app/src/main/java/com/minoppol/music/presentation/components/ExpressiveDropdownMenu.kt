package com.minoppol.music.presentation.components

import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList

/**
 * Material 3 expressive menu: one rounded group whose items take leading, middle and trailing
 * shapes. [vibrant] switches to the tertiary-based container for menus that sit on prominent UI.
 * Picking an action dismisses the menu before running it.
 */
@Composable
fun ExpressiveDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: ImmutableList<DetailHeroMenuAction>,
    modifier: Modifier = Modifier,
    vibrant: Boolean = false,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(index = 0, count = 1),
            containerColor = if (vibrant) {
                MenuDefaults.groupVibrantContainerColor
            } else {
                MenuDefaults.groupStandardContainerColor
            },
        ) {
            actions.forEachIndexed { index, action ->
                DropdownMenuItem(
                    onClick = {
                        onDismissRequest()
                        action.onClick()
                    },
                    text = { Text(action.label) },
                    shape = MenuDefaults.itemShape(index = index, count = actions.size).shape,
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    colors = MenuDefaults.itemColors(),
                )
            }
        }
    }
}
