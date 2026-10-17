package cz.talkative.app.data.local

import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.Word

fun FolderEntity.toDomain() = Folder(id, profileId, title, parentId, sortOrder, updatedAt)

fun WordEntity.toDomain() = Word(id, profileId, title, parentId, sortOrder, updatedAt)
