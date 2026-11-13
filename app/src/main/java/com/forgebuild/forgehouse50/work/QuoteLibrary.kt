package com.forgebuild.forgehouse50.work

import java.util.Calendar
import java.util.TimeZone

/**
 * Curated, non-repeating spiritual & encouraging quotes for hourly re-engagement (k8).
 */
object QuoteLibrary {
    const val WAKING_START_HOUR = 7   // 07:00 Africa/Lagos
    const val WAKING_END_HOUR = 21    // Through 21:59 Africa/Lagos (quiet overnight 22:00-06:59)

    data class QuoteItem(
        val text: String,
        val reference: String,
    )

    val QUOTES: List<QuoteItem> = listOf(
        QuoteItem("Your word is a lamp for my feet, a light on my path.", "Psalm 119:105"),
        QuoteItem("Man shall not live on bread alone, but on every word that comes from the mouth of God.", "Matthew 4:4"),
        QuoteItem("Take five quiet minutes right now—open today's chapters and hear God speak.", "ForgeHouse 50"),
        QuoteItem("Let the message of Christ dwell among you richly as you read and reflect today.", "Colossians 3:16"),
        QuoteItem("Faith comes from hearing the message, and the message is heard through the word about Christ.", "Romans 10:17"),
        QuoteItem("Come to me, all you who are weary and burdened, and I will give you rest.", "Matthew 11:28"),
        QuoteItem("Do not let this Book of the Law depart from your mouth; meditate on it day and night.", "Joshua 1:8"),
        QuoteItem("In the beginning was the Word, and the Word was with God, and the Word was God.", "John 1:1"),
        QuoteItem("Consistency beats intensity. One faithful chapter right now keeps your heart anchored.", "ForgeHouse 50"),
        QuoteItem("Remain in me, as I also remain in you. Apart from me you can do nothing.", "John 15:4-5"),
        QuoteItem("The grass withers and the flowers fall, but the word of our God endures forever.", "Isaiah 40:8"),
        QuoteItem("I can do all this through him who gives me strength.", "Philippians 4:13"),
        QuoteItem("Pause the noise of the day for one chapter. Let Scripture reset your mind.", "ForgeHouse 50"),
        QuoteItem("Blessed are those who hunger and thirst for righteousness, for they will be filled.", "Matthew 5:6"),
        QuoteItem("Peace I leave with you; my peace I give you. Do not let your hearts be troubled.", "John 14:27"),
        QuoteItem("Seek first his kingdom and his righteousness, and all these things will be given to you.", "Matthew 6:33"),
        QuoteItem("For the word of God is alive and active. Sharper than any double-edged sword.", "Hebrews 4:12"),
        QuoteItem("Wait for the Lord; be strong and take heart and wait for the Lord.", "Psalm 27:14"),
        QuoteItem("A single verse pondered deeply can carry your spirit through the entire afternoon.", "ForgeHouse 50"),
        QuoteItem("Rejoice always, pray continually, give thanks in all circumstances.", "1 Thessalonians 5:16-18"),
        QuoteItem("Do not conform to the pattern of this world, but be transformed by the renewing of your mind.", "Romans 12:2"),
        QuoteItem("Have I not commanded you? Be strong and courageous. Do not be afraid.", "Joshua 1:9"),
        QuoteItem("The Lord is my shepherd, I lack nothing. He refreshes my soul.", "Psalm 23:1-3"),
        QuoteItem("Write one honest observation or prayer from your reading today—it helps truth take root.", "ForgeHouse 50"),
        QuoteItem("Cast all your anxiety on him because he cares for you.", "1 Peter 5:7"),
        QuoteItem("Let us run with perseverance the race marked out for us, fixing our eyes on Jesus.", "Hebrews 12:1-2"),
        QuoteItem("All Scripture is God-breathed and is useful for teaching, rebuking, correcting and training.", "2 Timothy 3:16"),
        QuoteItem("Even five verses with an open heart is better than rushing past the day without truth.", "ForgeHouse 50"),
        QuoteItem("The light shines in the darkness, and the darkness has not overcome it.", "John 1:5"),
        QuoteItem("Be still, and know that I am God.", "Psalm 46:10"),
        QuoteItem("Blessed is the one who perseveres under trial, because having stood the test they will receive life.", "James 1:12"),
        QuoteItem("My grace is sufficient for you, for my power is made perfect in weakness.", "2 Corinthians 12:9"),
        QuoteItem("Walk together with the community—check today's reading and encourage someone in chat.", "ForgeHouse 50"),
        QuoteItem("Therefore go and make disciples of all nations... And surely I am with you always.", "Matthew 28:19-20"),
        QuoteItem("For God has not given us a spirit of fear, but of power and of love and of a sound mind.", "2 Timothy 1:7"),
        QuoteItem("He who began a good work in you will carry it on to completion until the day of Christ Jesus.", "Philippians 1:6"),
    )

    fun nextQuoteIndex(lastIndex: Int, total: Int = QUOTES.size): Int {
        if (total <= 0) return 0
        val next = lastIndex + 1
        return if (next < 0) 0 else next % total
    }

    fun isWithinWakingHours(
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getTimeZone("Africa/Lagos"),
    ): Boolean {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in WAKING_START_HOUR..WAKING_END_HOUR
    }

    fun shouldShowQuoteNotification(
        isLoggedIn: Boolean,
        enabled: Boolean,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getTimeZone("Africa/Lagos"),
    ): Boolean = isLoggedIn && enabled && isWithinWakingHours(nowMs, timeZone)
}
