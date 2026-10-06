package com.minoppol.music.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.size.Size
import com.minoppol.music.R
import com.minoppol.music.data.lxmusic.LxArtist
import com.minoppol.music.data.lxmusic.LxSources
import com.minoppol.music.ui.theme.RoundedSans

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LxArtistPickerBottomSheet(
    artists: List<LxArtist>,
    source: String,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onArtistClick: (LxArtist) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val countLabel = when (artists.size) {
        1 -> stringResource(R.string.artist_picker_count_single)
        else -> stringResource(R.string.artist_picker_count_multiple, artists.size)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        containerColor = colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.artist_picker_title),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = RoundedSans,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp)),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                artists.forEachIndexed { index, artist ->
                    LxArtistPickerCard(
                        artist = artist,
                        source = source,
                        shape = lxArtistPickerItemShape(index = index, count = artists.size),
                        onClick = { onArtistClick(artist) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LxArtistPickerCard(
    artist: LxArtist,
    source: String,
    shape: RoundedCornerShape,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = colorScheme.surfaceContainerLow
    val contentColor = colorScheme.onSurface
    val avatarSize = 52.dp

    Surface(
        onClick = onClick,
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                SmartImage(
                    model = artist.pic,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    placeholderResId = R.drawable.rounded_artist_24,
                    errorResId = R.drawable.rounded_artist_24,
                    shape = CircleShape,
                    contentScale = ContentScale.Crop,
                    targetSize = Size(180, 180),
                    placeHolderBackgroundColor = Color.Transparent
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = RoundedSans,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    color = LxSources.brandColor(source),
                    shape = CircleShape
                ) {
                    Text(
                        text = LxSources.shortLabel(source),
                        style = MaterialTheme.typography.labelMedium,
                        color = LxSources.onBrandColor(source),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = contentColor
                )
            }
        }
    }
}

private fun lxArtistPickerItemShape(
    index: Int,
    count: Int
): RoundedCornerShape {
    val outerCorner = 26.dp
    val innerCorner = 10.dp
    return when {
        count <= 1 -> RoundedCornerShape(outerCorner)
        index == 0 -> RoundedCornerShape(
            topStart = outerCorner,
            topEnd = outerCorner,
            bottomStart = innerCorner,
            bottomEnd = innerCorner
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = innerCorner,
            topEnd = innerCorner,
            bottomStart = outerCorner,
            bottomEnd = outerCorner
        )
        else -> RoundedCornerShape(innerCorner)
    }
}
