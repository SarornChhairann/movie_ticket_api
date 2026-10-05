package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.enums.MovieStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Curated Phnom Penh, Siem Reap, and Sihanoukville demo data.
 * Posters, banners, and food photos are clean designed stills in src/main/resources/seed.
 */
public final class CambodiaSeedCatalog {

    private CambodiaSeedCatalog() {
    }

    public record PersonSeed(String name, String email, String phone, int points) {
    }

    public record CinemaSeed(
            String name,
            String shortName,
            String city,
            String address,
            String openingHours,
            double latitude,
            double longitude) {
    }

    public record HallPlan(String suffix, String type, boolean dolbyAtmos, String amenities) {
    }

    public record MovieSeed(
            String slug,
            String title,
            String description,
            String genre,
            String director,
            String cast,
            String language,
            String rating,
            LocalDate releaseDate,
            int durationMinutes,
            MovieStatus status) {
    }

    public record FoodSeed(String slug, String name, String description, BigDecimal price) {
    }

    public record PromoSeed(String code, int discountPercent, BigDecimal minPurchase, int maxUses) {
    }

    public static final List<HallPlan> HALL_PLANS = List.of(
            new HallPlan("Atmos", "ATMOS", true, "Dolby Atmos, អេក្រង់ 4K, កៅអីរាងអង្គុយ"),
            new HallPlan("Standard 1", "STANDARD", false, "អេក្រង់ 2D, សំឡេងឌីជីថល, ម៉ាស៊ីនត្រជាក់"),
            new HallPlan("Standard 2", "STANDARD", false, "អេក្រង់ 2D, សំឡេងឌីជីថល, ម៉ាស៊ីនត្រជាក់")
    );

    public static final List<PersonSeed> CUSTOMERS = List.of(
            new PersonSeed("សុខា ចាន់", "sokha.chan@gmail.com", "012 834 221", 420),
            new PersonSeed("ដារ៉ា មាស", "dara.meas@gmail.com", "010 223 4418", 180),
            new PersonSeed("ស្រីមុំ កែវ", "sreymom.keo@gmail.com", "011 552 903", 760),
            new PersonSeed("វិរៈ សុខ", "vireak.sok@gmail.com", "015 778 104", 95),
            new PersonSeed("បូរ៉ា លី", "bora.ly@gmail.com", "016 441 228", 310),
            new PersonSeed("ចន្ទ្រា ហែម", "chanthra.hem@gmail.com", "017 903 552", 40),
            new PersonSeed("ពិសិដ្ឋ ង៉ុយ", "piseth.nguon@gmail.com", "061 220 847", 540),
            new PersonSeed("ម៉ាលី ពៅ", "maly.pov@gmail.com", "077 615 330", 125),
            new PersonSeed("រតនា គឹម", "rothana.kim@gmail.com", "078 904 116", 880),
            new PersonSeed("សុភា អ៊ុយ", "sophea.uy@gmail.com", "096 332 780", 60),
            new PersonSeed("ណារី តាន់", "nary.tan@gmail.com", "097 448 215", 250),
            new PersonSeed("ពិសី ឈឿន", "pisei.chhoeun@gmail.com", "098 771 064", 15),
            new PersonSeed("សុវណ្ណ អ៊ុក", "sovann.ouk@gmail.com", "088 256 419", 670),
            new PersonSeed("ចាន់រី សៅ", "chanry.sao@gmail.com", "081 903 772", 200),
            new PersonSeed("ធារី ម៉ៅ", "thary.mao@gmail.com", "071 664 128", 90),
            new PersonSeed("វណ្ណៈ ហង្ស", "vanna.hang@gmail.com", "070 228 953", 430),
            new PersonSeed("ស្រីពេជ្រ យឹម", "sreypech.yim@gmail.com", "089 517 340", 355),
            new PersonSeed("ដាណែត ឡុង", "danet.long@gmail.com", "093 882 016", 20),
            new PersonSeed("ពេជ្រ គង់", "pech.kong@gmail.com", "095 441 708", 510),
            new PersonSeed("សុខុម មុត", "sokhom.mut@gmail.com", "099 336 824", 140),
            new PersonSeed("លីណា ចេង", "lina.cheng@gmail.com", "076 290 551", 275),
            new PersonSeed("រាត្រី នុប", "reatrei.nop@gmail.com", "092 708 163", 605),
            new PersonSeed("មករា ទេព", "makara.tep@gmail.com", "086 154 992", 85),
            new PersonSeed("សុភាព អ៊ឹម", "sopheap.im@gmail.com", "087 663 407", 330)
    );

    public static final List<CinemaSeed> CINEMAS = List.of(
            new CinemaSeed(
                    "រោងភាពយន្ត Legend Aeon Sen Sok",
                    "Legend Sen Sok",
                    "ភ្នំពេញ",
                    "ផ្សារទំនើបអ៊ីអន ២, ផ្លូវ ១០០៣, ខណ្ឌសែនសុខ, ភ្នំពេញ",
                    "09:00 - 23:00",
                    11.5778,
                    104.8868),
            new CinemaSeed(
                    "រោងភាពយន្ត Legend TK Avenue",
                    "Legend TK",
                    "ភ្នំពេញ",
                    "ផ្សារ TK Avenue, ផ្លូវ ៣១៥, ខណ្ឌទួលគោក, ភ្នំពេញ",
                    "09:00 - 23:00",
                    11.5794,
                    104.9015),
            new CinemaSeed(
                    "រោងភាពយន្ត Legend Midtown",
                    "Legend Midtown",
                    "ភ្នំពេញ",
                    "ផ្សារ Midtown, មហាវិថីម៉ៅសេទុង, ខណ្ឌចំការមន, ភ្នំពេញ",
                    "09:00 - 23:30",
                    11.5472,
                    104.9176),
            new CinemaSeed(
                    "Sabay Cineplex Aeon Mall",
                    "Major Aeon 1",
                    "ភ្នំពេញ",
                    "ផ្សារទំនើបអ៊ីអន ១, មហាវិថីសឺធារស, ខណ្ឌចំការមន, ភ្នំពេញ",
                    "09:00 - 23:00",
                    11.5483,
                    104.9315),
            new CinemaSeed(
                    "រោងភាពយន្ត Legend Siem Reap",
                    "Legend Siem Reap",
                    "សៀមរាប",
                    "The Heritage Walk, ក្រុងសៀមរាប, ខេត្តសៀមរាប",
                    "10:00 - 22:30",
                    13.3612,
                    103.8598),
            new CinemaSeed(
                    "រោងភាពយន្ត Legend Sihanoukville",
                    "Legend Sihanoukville",
                    "ព្រះសីហនុ",
                    "Prince Mall, ក្រុងព្រះសីហនុ, ខេត្តព្រះសីហនុ",
                    "10:00 - 22:30",
                    10.6260,
                    103.5235)
    );

    public static final List<MovieSeed> MOVIES = List.of(
            movie("jailbreak", "ពន្ធនាគារ (Jailbreak)",
                    "រឿងសកម្មភាពខ្មែរឆ្នាំ ២០១៧។ ពេលមានការបះបោរក្នុងពន្ធនាគារ ក្រុមអ្នកទោសត្រូវតស៊ូដើម្បីរស់ និងរកផ្លូវចេញ។",
                    "សកម្មភាព", "Jimmy Henderson",
                    "Dara Our, Jean-Paul Ly, Tharoth Sam, Savin Phillip",
                    "ខ្មែរ", "PG-13", LocalDate.of(2017, 5, 26), 102, MovieStatus.NOW_SHOWING),
            movie("prey", "សត្វព្រៃ (The Prey)",
                    "រឿងភ័យរន្ធត់ឆ្នាំ ២០១៨។ ប៉ូលិសម្នាក់ត្រូវបានគេទម្លាក់ចូលព្រៃ ដើម្បីឲ្យក្រុមអ្នកប្រមាញ់មនុស្សដេញតាម។",
                    "ភ័យរន្ធត់", "Jimmy Henderson",
                    "Gu Shangwei, Byron Bishop, Sahajak Boonthanakit",
                    "ខ្មែរ", "R", LocalDate.of(2018, 9, 7), 93, MovieStatus.NOW_SHOWING),
            movie("father", "ដំបូង គេសម្លាប់ឪពុកខ្ញុំ",
                    "ផ្អែកលើអនុស្សាវរីយ៍របស់ Loung Ung។ ក្មេងស្រីម្នាក់បាត់បង់គ្រួសារក្នុងសម័យខ្មែរក្រហម ហើយត្រូវរស់នៅក្នុងជំរំ។ ចេញផ្សាយឆ្នាំ ២០១៧។",
                    "រឿងល្ខោន", "Angelina Jolie",
                    "Sareum Srey Moch, Phoeung Kompheak, Sveng Socheata",
                    "ខ្មែរ", "R", LocalDate.of(2017, 9, 15), 136, MovieStatus.NOW_SHOWING),
            movie("diamond", "កោះពេជ្រ (Diamond Island)",
                    "ក្មេងជនបទម្នាក់មករកការងារនៅកោះពេជ្រ ភ្នំពេញ ហើយជួបបងប្អូន និងជីវិតទីក្រុង។ ដឹកនាំដោយ Davy Chou ឆ្នាំ ២០១៦។",
                    "រឿងល្ខោន", "Davy Chou",
                    "Sobon Nuon, Cheanick Nov, Madeza Chhim",
                    "ខ្មែរ", "PG-13", LocalDate.of(2016, 10, 26), 101, MovieStatus.NOW_SHOWING),
            movie("karmalink", "ខាម៉ាលីង (Karmalink)",
                    "រឿងវិទ្យាសាស្ត្រនៅភ្នំពេញឆ្នាំ ២០២១។ ក្មេងប្រុសសុបិនឃើញជីវិតមុន ហើយស្វែងរកព្រះពុទ្ធបដិមាតូចមួយ។",
                    "វិទ្យាសាស្ត្រ", "Jake Wachtel",
                    "Leng Heng Prak, Srey Leak Chhith, Sahajak Boonthanakit",
                    "ខ្មែរ", "PG-13", LocalDate.of(2021, 8, 20), 102, MovieStatus.NOW_SHOWING),
            movie("lastreel", "រឿងចុងក្រោយ (The Last Reel)",
                    "ស្រីស្រស់ម្នាក់ជួបតារាសម្តែងចាស់ ហើយស្វែងរករឿងភាពយន្តខ្មែរដែលមិនទាន់បានបញ្ចប់។ ឆ្នាំ ២០១៤។",
                    "រឿងល្ខោន", "Kulikar Sotho",
                    "Ma Rynet, Sok Sothun, Dy Saveth, Hun Sophy",
                    "ខ្មែរ", "PG", LocalDate.of(2014, 11, 7), 105, MovieStatus.NOW_SHOWING),
            movie("music", "ក្នុងជីវិតតន្ត្រី",
                    "រឿងស្នេហាតាមបទចម្រៀងមួយ ឆ្លងកាត់បីជំនាន់នៅកម្ពុជា។ ដឹកនាំដោយ Caylee So និង Sok Visal ឆ្នាំ ២០១៨។",
                    "ស្នេហា", "Caylee So, Sok Visal",
                    "Daniel Kok, Socheata Chhon, Cindy Bishop",
                    "ខ្មែរ", "PG", LocalDate.of(2018, 11, 9), 100, MovieStatus.NOW_SHOWING),
            movie("forgotten", "កុំគិតថាខ្ញុំភ្លេច",
                    "ឯកសារអំពីតន្ត្រីរ៉ុកខ្មែរមុនឆ្នាំ ១៩៧៥ និងសិល្បករដែលបាត់បង់ក្នុងសម័យខ្មែរក្រហម។ ដឹកនាំដោយ John Pirozzi។",
                    "ឯកសារ", "John Pirozzi",
                    "Sinn Sisamouth, Ros Serey Sothea, Pen Ran",
                    "ខ្មែរ", "PG", LocalDate.of(2015, 4, 22), 105, MovieStatus.NOW_SHOWING),
            movie("ruin", "បាក់បែក (Ruin)",
                    "បុរស និងស្ត្រីពីរនាក់ដើរក្នុងភ្នំពេញយប់ ដោយគ្មានផែនការ។ រឿងឯករាជ្យឆ្នាំ ២០១៣។",
                    "រឿងល្ខោន", "Michael Cody, Amiel Courtin-Wilson",
                    "Rous Mony, Sang Malen",
                    "ខ្មែរ", "R", LocalDate.of(2013, 9, 6), 90, MovieStatus.NOW_SHOWING),
            movie("evening", "ល្ងាចមួយក្រោយសង្គ្រាម",
                    "ក្រោយសង្គ្រាម ទាហានម្នាក់ត្រឡប់មកភ្នំពេញ ហើយស្គាល់ស្រីស្រែកក្នុងបារ។ ដឹកនាំដោយ Rithy Panh ឆ្នាំ ១៩៩៨។",
                    "រឿងល្ខោន", "Rithy Panh",
                    "Chea Lyda Chan, Ratha Keo",
                    "ខ្មែរ", "R", LocalDate.of(1998, 5, 20), 108, MovieStatus.NOW_SHOWING),
            movie("slumbers", "ដំណេកមាស (Golden Slumbers)",
                    "ឯកសាររបស់ Davy Chou អំពីភាពយន្តខ្មែរមុនឆ្នាំ ១៩៧៥ ដែលខ្សែភាពយន្តភាគច្រើនបានបាត់។ ការបញ្ចាំងត្រឡប់មកវិញ។",
                    "ឯកសារ", "Davy Chou",
                    "Ly Bun Yim, Ly You Sreang, Dy Saveth",
                    "ខ្មែរ", "PG", LocalDate.of(2011, 9, 2), 96, MovieStatus.COMING_SOON),
            movie("missing", "រូបភាពដែលបាត់",
                    "ឯកសាររបស់ Rithy Panh ឆ្នាំ ២០១៣ ប្រើរូបដីឥដ្ឋ ដើម្បីរំឭកជីវិតក្នុងសម័យខ្មែរក្រហម។ ការបញ្ចាំងពិសេស។",
                    "ឯកសារ", "Rithy Panh",
                    "Randal Douc",
                    "ខ្មែរ", "PG-13", LocalDate.of(2013, 10, 30), 92, MovieStatus.COMING_SOON),
            movie("rice", "អ្នកស្រែ (Rice People)",
                    "គ្រួសារអ្នកស្រែមួយនៅតំបន់បឹង តស៊ូជាមួយជំងឺ និងរដូវធ្វើស្រែ។ ដឹកនាំដោយ Rithy Panh ឆ្នាំ ១៩៩៤។",
                    "រឿងល្ខោន", "Rithy Panh",
                    "Peng Phan, Mom Soth",
                    "ខ្មែរ", "PG", LocalDate.of(1994, 5, 20), 125, MovieStatus.COMING_SOON),
            movie("exile", "និរទេស (Exile)",
                    "ឯកសារឆ្នាំ ២០១៦ របស់ Rithy Panh អំពីការនិរទេសខ្លួន និងការចងចាំ។ ការបញ្ចាំងពិសេសនៅភ្នំពេញ។",
                    "ឯកសារ", "Rithy Panh",
                    "Randal Douc",
                    "ខ្មែរ", "PG", LocalDate.of(2016, 2, 17), 77, MovieStatus.COMING_SOON),
            movie("graves", "ផ្នូរឥតឈ្មោះ",
                    "ឯកសារឆ្នាំ ២០១៨ របស់ Rithy Panh ស្វែងរកផ្នូរគ្រួសារដែលបាត់ក្នុងសម័យខ្មែរក្រហម។ ការបញ្ចាំងត្រឡប់មកវិញ។",
                    "ឯកសារ", "Rithy Panh",
                    "Rithy Panh",
                    "ខ្មែរ", "PG-13", LocalDate.of(2018, 11, 14), 115, MovieStatus.COMING_SOON)
    );

    public static final List<FoodSeed> FOOD = List.of(
            new FoodSeed("popcorn-small", "ពោតលីងតូច", "ពោតលីងថ្មីៗ ទំហំតូច សម្រាប់ម្នាក់។", money("2.50")),
            new FoodSeed("popcorn-large", "ពោតលីងធំ", "ធុងពោតលីងទំហំធំ សម្រាប់ពីរនាក់។", money("4.50")),
            new FoodSeed("popcorn-bucket", "ពោតលីងធុង", "ធុងពោតលីងសម្រាប់ក្រុម។", money("6.00")),
            new FoodSeed("cola", "សូដាកូឡា", "កែវសូដាត្រជាក់ ទំហំមធ្យម។", money("1.50")),
            new FoodSeed("coffee", "កាហ្វេទឹកកក", "កាហ្វេទឹកកកបែបខ្មែរ លាយទឹកដោះគោ។", money("2.00")),
            new FoodSeed("tea", "តែទឹកកក", "តែទឹកកកផ្អែម ទឹកកកពេញកែវ។", money("1.50")),
            new FoodSeed("hotdog", "ហតដុក", "ហតដុកនំបុ័ង ជាមួយទឹកជ្រលក់។", money("3.00")),
            new FoodSeed("chicken", "មាន់បំពង", "មាន់បំពងក្រួស មួយប្រអប់។", money("4.00")),
            new FoodSeed("chocolate", "សូកូឡា", "ដុំសូកូឡាមួយ។", money("1.50")),
            new FoodSeed("skewers", "សាច់អាំង", "សាច់អាំងឈើពីរឈើ បែបផ្សាររាត្រីភ្នំពេញ។", money("3.50"))
    );

    public static final List<PromoSeed> PROMOS = List.of(
            new PromoSeed("KHMER15", 15, money("8.00"), 200),
            new PromoSeed("WEEKEND10", 10, money("5.00"), 300),
            new PromoSeed("STUDENT20", 20, money("6.00"), 150),
            new PromoSeed("SENSOK10", 10, null, 0),
            new PromoSeed("FAMILY25", 25, money("15.00"), 80)
    );

    public static final String[] PAYMENT_METHODS = {"ABA_PAY", "WING", "ACLEDA", "CASH"};

    public static final BigDecimal STANDARD_SEAT = money("4.00");
    public static final BigDecimal STANDARD_COUPLE = money("9.00");
    public static final BigDecimal ATMOS_SEAT = money("6.50");
    public static final BigDecimal ATMOS_COUPLE = money("14.00");

    private static MovieSeed movie(String slug, String title, String description, String genre, String director,
                                   String cast, String language, String rating, LocalDate releaseDate,
                                   int durationMinutes, MovieStatus status) {
        return new MovieSeed(slug, title, description, genre, director, cast, language, rating,
                releaseDate, durationMinutes, status);
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }
}
