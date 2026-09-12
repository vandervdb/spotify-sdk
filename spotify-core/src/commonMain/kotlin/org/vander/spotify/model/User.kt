package org.vander.spotify.model

public data class User(
    public val id: UserId,
    public val displayName: String? = null,
    public val email: String? = null,
    public val images: List<Image> = emptyList(),
) {
    public val avatar: Image? get() = images.firstOrNull()
}
