package org.vander.spotify.model

public data class User(
    public val id: UserId,
    public val displayName: String? = null,
    public val email: String? = null,
    public val images: List<Image> = emptyList(),
    /** Offre de l'abonnement telle que Spotify la rend (`premium`, `free`…), `null` si absente. */
    public val product: String? = null,
) {
    public val avatar: Image? get() = images.firstOrNull()

    /** La lecture par l'App Remote exige un compte Premium. */
    public val isPremium: Boolean get() = product.equals("premium", ignoreCase = true)
}
