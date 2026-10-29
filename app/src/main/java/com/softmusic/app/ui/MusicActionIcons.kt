package com.softmusic.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.softmusic.app.R

/** Action vectors for the dark interface; choose Selected only for a persistent active state. */
internal object MusicActionIcons {
    val Rename: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_rename_normal)

    val RenameSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_rename_selected)

    val EditMetadata: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_edit_metadata_normal)

    val EditMetadataSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_edit_metadata_selected)

    val AddSong: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_add_song_normal)

    val AddSongSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_add_song_selected)

    val AddManySongs: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_add_many_songs_normal)

    val AddManySongsSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_add_many_songs_selected)

    val AddToPlaylist: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_add_to_playlist_normal)

    val AddToPlaylistSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_add_to_playlist_selected)

    val RemoveFromPlaylist: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_remove_from_playlist_normal)

    val RemoveFromPlaylistSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_remove_from_playlist_selected)

    val NewPlaylist: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_new_playlist_normal)

    val NewPlaylistSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_new_playlist_selected)

    val DuplicatePlaylist: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_duplicate_playlist_normal)

    val DuplicatePlaylistSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_duplicate_playlist_selected)

    val MergePlaylists: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_merge_playlists_normal)

    val MergePlaylistsSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_merge_playlists_selected)

    val ChangeCover: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_change_cover_normal)

    val ChangeCoverSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_change_cover_selected)

    val PinPlaylist: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_pin_playlist_normal)

    val PinPlaylistSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_pin_playlist_selected)

    val DeletePlaylist: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_delete_playlist_normal)

    val DeletePlaylistSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_delete_playlist_selected)

    val SelectItem: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_select_item_normal)

    val SelectItemSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_select_item_selected)

    val SelectionMode: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_selection_mode_normal)

    val SelectionModeSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_selection_mode_selected)

    val SelectAll: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_select_all_normal)

    val SelectAllSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_select_all_selected)

    val DeselectAll: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_deselect_all_normal)

    val DeselectAllSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_deselect_all_selected)

    val InvertSelection: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_invert_selection_normal)

    val InvertSelectionSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_invert_selection_selected)

    val SelectRange: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_select_range_normal)

    val SelectRangeSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_select_range_selected)

    val SelectSongs: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_select_songs_normal)

    val SelectSongsSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_select_songs_selected)

    val SelectedCount: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_selected_count_normal)

    val SelectedCountSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_selected_count_selected)

    val SortMenu: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_menu_normal)

    val SortMenuSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_menu_selected)

    val SortTitleAz: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_title_az_normal)

    val SortTitleAzSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_title_az_selected)

    val SortTitleZa: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_title_za_normal)

    val SortTitleZaSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_title_za_selected)

    val SortArtist: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_artist_normal)

    val SortArtistSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_artist_selected)

    val SortAlbum: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_album_normal)

    val SortAlbumSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_album_selected)

    val SortDuration: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_duration_normal)

    val SortDurationSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_duration_selected)

    val SortAdded: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_added_normal)

    val SortAddedSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_added_selected)

    val SortModified: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_modified_normal)

    val SortModifiedSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_modified_selected)

    val SortTrackNumber: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_track_number_normal)

    val SortTrackNumberSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_track_number_selected)

    val SortFileName: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_file_name_normal)

    val SortFileNameSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_file_name_selected)

    val SortSize: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_size_normal)

    val SortSizeSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_size_selected)

    val SortAscending: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_ascending_normal)

    val SortAscendingSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_ascending_selected)

    val SortDescending: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_descending_normal)

    val SortDescendingSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_descending_selected)

    val SortCustom: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_custom_normal)

    val SortCustomSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_sort_custom_selected)

    val ResetOrder: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_reset_order_normal)

    val ResetOrderSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_reset_order_selected)

    val MoveUp: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_move_up_normal)

    val MoveUpSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_move_up_selected)

    val MoveDown: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_move_down_normal)

    val MoveDownSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_move_down_selected)

    val MoveToTop: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_move_to_top_normal)

    val MoveToTopSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_move_to_top_selected)

    val MoveToBottom: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_move_to_bottom_normal)

    val MoveToBottomSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_move_to_bottom_selected)

    val DragReorder: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_drag_reorder_normal)

    val DragReorderSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_drag_reorder_selected)

    val MoveToPlaylist: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_move_to_playlist_normal)

    val MoveToPlaylistSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_move_to_playlist_selected)

    val AddToQueue: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_add_to_queue_normal)

    val AddToQueueSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_add_to_queue_selected)

    val ClearQueue: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_clear_queue_normal)

    val ClearQueueSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_clear_queue_selected)

    val RemoveDuplicates: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_remove_duplicates_normal)

    val RemoveDuplicatesSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_remove_duplicates_selected)

    val LocateFile: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_locate_file_normal)

    val LocateFileSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_locate_file_selected)

    val SongDetails: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_song_details_normal)

    val SongDetailsSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_song_details_selected)

    val SaveChanges: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_save_changes_normal)

    val SaveChangesSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_save_changes_selected)

    val Undo: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_undo_normal)

    val UndoSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_undo_selected)

    val Redo: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_redo_normal)

    val RedoSelected: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_sm_action_redo_selected)
}
