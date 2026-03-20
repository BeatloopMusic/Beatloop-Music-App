package com.beatloop.music.ui.onboarding

import java.net.URLEncoder

data class OnboardingArtistOption(
    val name: String,
    val imageUrl: String,
    val bestSong: String,
    val languages: Set<String>
)

data class OnboardingPersonOption(
    val name: String,
    val languages: Set<String>
)

object OnboardingCatalog {
    val indianLanguages = listOf(
        "Hindi",
        "Telugu",
        "Tamil",
        "Kannada",
        "Malayalam",
        "Punjabi",
        "Bengali",
        "Marathi",
        "Gujarati",
        "Odia"
    )

    val internationalLanguages = listOf(
        "English",
        "Spanish",
        "Korean",
        "Japanese",
        "Arabic",
        "French",
        "Portuguese",
        "Turkish"
    )

    val allLanguages: List<String> = indianLanguages + internationalLanguages

    fun singerPortraitUrl(rawUrl: String): String {
        val encodedSource = URLEncoder.encode(rawUrl.removePrefix("https://"), "UTF-8")
        return "https://images.weserv.nl/?url=$encodedSource&w=560&h=560&fit=cover&a=attention&output=webp&q=90"
    }

    fun singerFallbackUrl(name: String): String {
        val encodedName = URLEncoder.encode(name, "UTF-8")
        return "https://ui-avatars.com/api/?name=$encodedName&size=560&background=111827&color=f8fafc&bold=true&rounded=true&format=png"
    }

    val singers = listOf(
        OnboardingArtistOption(
            name = "Arijit Singh",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/b/b7/Arijit_Singh_performance_at_Chandigarh_2025.jpg",
            bestSong = "Tum Hi Ho",
            languages = setOf("Hindi", "Bengali")
        ),
        OnboardingArtistOption(
            name = "Shreya Ghoshal",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/a/a0/Shreya_Ghoshal_Behindwoods_Gold_Icons_Awards_2023_%28cropped%29.jpg",
            bestSong = "Agar Tum Mil Jao",
            languages = setOf("Hindi", "Bengali", "Tamil", "Telugu", "Kannada", "Malayalam")
        ),
        OnboardingArtistOption(
            name = "Sonu Nigam",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/7/76/Sonu_Nigam123.jpg",
            bestSong = "Abhi Mujh Mein Kahin",
            languages = setOf("Hindi", "Kannada", "Bengali")
        ),
        OnboardingArtistOption(
            name = "KK",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/f/f2/Singer_KK.jpg",
            bestSong = "Khuda Jaane",
            languages = setOf("Hindi", "Tamil", "Telugu")
        ),
        OnboardingArtistOption(
            name = "Sid Sriram",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/1/1f/SidSriramLive.jpg",
            bestSong = "Srivalli",
            languages = setOf("Tamil", "Telugu", "Kannada", "Malayalam")
        ),
        OnboardingArtistOption(
            name = "S. P. Balasubrahmanyam",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/2/29/SPB_in_2015.jpg",
            bestSong = "Tere Mere Beech Mein",
            languages = setOf("Telugu", "Tamil", "Kannada", "Hindi", "Malayalam")
        ),
        OnboardingArtistOption(
            name = "K. S. Chithra",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/1/1c/K.S._Chithra.jpg",
            bestSong = "Kannalane",
            languages = setOf("Malayalam", "Tamil", "Telugu", "Kannada", "Hindi")
        ),
        OnboardingArtistOption(
            name = "Vijay Prakash",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/2/2f/Vijay_Prakash.jpg",
            bestSong = "Hosanna",
            languages = setOf("Kannada", "Tamil", "Telugu", "Hindi")
        ),
        OnboardingArtistOption(
            name = "Jubin Nautiyal",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/a/a4/Jubin_Nautiyal.jpg",
            bestSong = "Raataan Lambiyan",
            languages = setOf("Hindi")
        ),
        OnboardingArtistOption(
            name = "Diljit Dosanjh",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/7/76/Diljit_Dosanjh_2018.jpg",
            bestSong = "Do You Know",
            languages = setOf("Punjabi", "Hindi")
        ),
        OnboardingArtistOption(
            name = "Atif Aslam",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/2/24/Atif_Aslam_in_2012.jpg",
            bestSong = "Jeena Jeena",
            languages = setOf("Hindi", "Punjabi")
        ),
        OnboardingArtistOption(
            name = "Shankar Mahadevan",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/8/8f/Shankar_Mahadevan_at_Aatmnirbhar_Bharat_event.jpg",
            bestSong = "Breathless",
            languages = setOf("Hindi", "Tamil", "Telugu", "Kannada", "Marathi")
        ),
        OnboardingArtistOption(
            name = "Asha Bhosle",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/8/82/Asha_Bhosle_at_18th_Annual_Star_Screen_Awards.jpg",
            bestSong = "Piya Tu Ab To Aaja",
            languages = setOf("Hindi", "Marathi", "Bengali")
        ),
        OnboardingArtistOption(
            name = "Lata Mangeshkar",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/1/1d/Lata_Mangeshkar_at_Veer-Zaara_music_launch.jpg",
            bestSong = "Lag Ja Gale",
            languages = setOf("Hindi", "Marathi", "Bengali")
        ),
        OnboardingArtistOption(
            name = "Pritam",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/8/81/Pritam_Chakraborty.jpg",
            bestSong = "Kesariya",
            languages = setOf("Hindi", "Bengali")
        ),
        OnboardingArtistOption(
            name = "Ed Sheeran",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/4/45/Ed_Sheeran_2018.jpg",
            bestSong = "Shape of You",
            languages = setOf("English")
        ),
        OnboardingArtistOption(
            name = "Taylor Swift",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/f/f2/Taylor_Swift_2_-_2019_by_Glenn_Francis.jpg",
            bestSong = "Cruel Summer",
            languages = setOf("English")
        ),
        OnboardingArtistOption(
            name = "The Weeknd",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/9/95/The_Weeknd_in_2018.png",
            bestSong = "Blinding Lights",
            languages = setOf("English")
        ),
        OnboardingArtistOption(
            name = "Adele",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/7/7c/Adele_2016.jpg",
            bestSong = "Hello",
            languages = setOf("English")
        ),
        OnboardingArtistOption(
            name = "Bad Bunny",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/2/2e/Bad_Bunny_2019_by_Glenn_Francis.jpg",
            bestSong = "Tití Me Preguntó",
            languages = setOf("Spanish")
        ),
        OnboardingArtistOption(
            name = "Shakira",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/2/2c/Shakira_Rock_in_Rio_2018.jpg",
            bestSong = "Hips Don't Lie",
            languages = setOf("Spanish", "English")
        ),
        OnboardingArtistOption(
            name = "BTS",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/0/0d/BTS_for_Dispatch_White_Day_Special%2C_27_February_2019_04.jpg",
            bestSong = "Dynamite",
            languages = setOf("Korean", "English")
        ),
        OnboardingArtistOption(
            name = "IU",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/9/9a/IU_at_Incheon_Airport%2C_28_May_2019_03.jpg",
            bestSong = "Blueming",
            languages = setOf("Korean")
        ),
        OnboardingArtistOption(
            name = "Blackpink",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/e/e8/BLACKPINK_at_Incheon_Airport%2C_16_October_2019_01.jpg",
            bestSong = "How You Like That",
            languages = setOf("Korean", "English")
        ),
        OnboardingArtistOption(
            name = "Kenshi Yonezu",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/0/03/Kenshi_Yonezu_2019.jpg",
            bestSong = "Lemon",
            languages = setOf("Japanese")
        ),
        OnboardingArtistOption(
            name = "Hikaru Utada",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/6/6e/Hikaru_Utada_2018.jpg",
            bestSong = "First Love",
            languages = setOf("Japanese", "English")
        ),
        OnboardingArtistOption(
            name = "Amr Diab",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/f/f3/Amr_Diab.jpg",
            bestSong = "Tamally Maak",
            languages = setOf("Arabic")
        ),
        OnboardingArtistOption(
            name = "Nancy Ajram",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/0/0a/Nancy_Ajram_2012.jpg",
            bestSong = "Ah W Noss",
            languages = setOf("Arabic")
        )
    )

    val lyricists = listOf(
        OnboardingPersonOption("None", allLanguages.toSet()),
        OnboardingPersonOption("Gulzar", setOf("Hindi")),
        OnboardingPersonOption("Javed Akhtar", setOf("Hindi")),
        OnboardingPersonOption("Irshad Kamil", setOf("Hindi")),
        OnboardingPersonOption("Sahithi", setOf("Telugu")),
        OnboardingPersonOption("Sirivennela Seetharama Sastry", setOf("Telugu")),
        OnboardingPersonOption("Vairamuthu", setOf("Tamil")),
        OnboardingPersonOption("Thamarai", setOf("Tamil")),
        OnboardingPersonOption("Yogaraj Bhat", setOf("Kannada")),
        OnboardingPersonOption("B. K. Harinarayanan", setOf("Malayalam")),
        OnboardingPersonOption("Prasoon Joshi", setOf("Hindi")),
        OnboardingPersonOption("Swanand Kirkire", setOf("Hindi", "Marathi")),
        OnboardingPersonOption("Benny Blanco", setOf("English")),
        OnboardingPersonOption("Max Martin", setOf("English")),
        OnboardingPersonOption("RM", setOf("Korean", "English")),
        OnboardingPersonOption("Yojiro Noda", setOf("Japanese")),
        OnboardingPersonOption("Badshah", setOf("Hindi", "Punjabi")),
        OnboardingPersonOption("Anirudh Ravichander", setOf("Tamil", "Telugu"))
    )

    val musicDirectors = listOf(
        OnboardingPersonOption("A. R. Rahman", setOf("Hindi", "Tamil", "Telugu", "Malayalam")),
        OnboardingPersonOption("Anirudh Ravichander", setOf("Tamil", "Telugu")),
        OnboardingPersonOption("Devi Sri Prasad", setOf("Telugu", "Tamil")),
        OnboardingPersonOption("S. Thaman", setOf("Telugu", "Tamil")),
        OnboardingPersonOption("Pritam", setOf("Hindi", "Bengali")),
        OnboardingPersonOption("Amit Trivedi", setOf("Hindi")),
        OnboardingPersonOption("Vishal-Shekhar", setOf("Hindi")),
        OnboardingPersonOption("Ilaiyaraaja", setOf("Tamil", "Telugu", "Malayalam", "Kannada")),
        OnboardingPersonOption("Yuvan Shankar Raja", setOf("Tamil")),
        OnboardingPersonOption("Harris Jayaraj", setOf("Tamil", "Telugu")),
        OnboardingPersonOption("G. V. Prakash Kumar", setOf("Tamil", "Telugu")),
        OnboardingPersonOption("M. M. Keeravani", setOf("Telugu", "Hindi", "Tamil")),
        OnboardingPersonOption("Shankar-Ehsaan-Loy", setOf("Hindi")),
        OnboardingPersonOption("Santhosh Narayanan", setOf("Tamil", "Telugu")),
        OnboardingPersonOption("Hans Zimmer", setOf("English")),
        OnboardingPersonOption("Ludwig Goransson", setOf("English")),
        OnboardingPersonOption("Joe Hisaishi", setOf("Japanese")),
        OnboardingPersonOption("Ramin Djawadi", setOf("English"))
    )

    fun singersForLanguages(languages: Set<String>): List<OnboardingArtistOption> {
        if (languages.isEmpty()) return emptyList()
        return singers.filter { option -> option.languages.any { it in languages } }
    }

    fun lyricistsForLanguages(languages: Set<String>): List<OnboardingPersonOption> {
        if (languages.isEmpty()) return lyricists
        return lyricists.filter { option -> option.name == "None" || option.languages.any { it in languages } }
    }

    fun musicDirectorsForLanguages(languages: Set<String>): List<OnboardingPersonOption> {
        if (languages.isEmpty()) return musicDirectors
        return musicDirectors.filter { option -> option.languages.any { it in languages } }
    }
}
