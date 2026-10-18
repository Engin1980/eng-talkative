package cz.talkative.app.domain.image

/** Čistá matematika kolem zmenšování obrázků (bez závislosti na Androidu). */
object ImageScaling {

    /** Delší strana uloženého obrázku nepřesáhne tuto hodnotu (v px). */
    const val MAX_SIDE = 512

    /** Rozměry po zmenšení tak, aby delší strana byla nejvýše [maxSide]; poměr stran se zachová, nikdy se nezvětšuje. */
    fun scaledSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
        require(width > 0 && height > 0) { "image size must be positive" }
        val longest = maxOf(width, height)
        if (longest <= maxSide) return width to height
        val factor = maxSide.toDouble() / longest
        return maxOf(1, Math.round(width * factor).toInt()) to maxOf(1, Math.round(height * factor).toInt())
    }

    /**
     * Největší mocnina dvou pro `inSampleSize` dekodéru, při které delší strana stále není menší než [maxSide]
     * (dekódování ušetří paměť, ale obrázek se kvůli němu nikdy nezmenší pod cílovou velikost).
     */
    fun sampleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Int {
        require(width > 0 && height > 0) { "image size must be positive" }
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        return sample
    }
}
