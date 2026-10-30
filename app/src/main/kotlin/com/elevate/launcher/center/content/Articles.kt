package com.elevate.launcher.center.content

/**
 * Offline educational articles. Written as general information for adults and teens, not as medical advice.
 * Nothing here scores a person's looks, and nothing promises changes in height or face shape.
 */
class Sec(val heading: String, val paras: List<String>)

class Article(val key: String, val group: String, val title: String, val summary: String, val sections: List<Sec>) {
    fun text(): String = (listOf(title, summary) + sections.flatMap { listOf(it.heading) + it.paras }).joinToString(" ").lowercase()
}

class Ingredient(val name: String, val what: String, val watch: String)

object Articles {
    const val DISCLAIMER = "This is general information to help you learn. It is not medical advice and cannot diagnose or treat anything. If something worries you, talk to a qualified doctor."

    /** The height and growth guide, in reading order. */
    val growthGuide = listOf("growth_how", "growth_genes_puberty", "growth_basics", "growth_posture_bone", "growth_claims", "growth_doctor", "measure_height", "height_facts")

    val groups = listOf("Skincare", "Hair", "Hygiene", "Posture", "Height & growth", "Sleep", "Nutrition", "Habits", "Training basics")

    private fun a(key: String, group: String, title: String, summary: String, vararg secs: Sec) =
        Article(key, group, title, summary, secs.toList())

    private fun s(h: String, vararg p: String) = Sec(h, p.toList())

    val all: List<Article> = listOf(
        // ------------------------------------------------------------ skincare
        a("skin_basics", "Skincare", "Skincare basics",
            "A simple routine is enough for most people: cleanse, moisturise, protect from the sun.",
            s("Start small", "Three steps cover most needs: a gentle cleanser, a moisturiser, and sunscreen in the morning. More products do not mean better skin, and each new product is another chance of irritation."),
            s("Know your skin type, loosely", "• Dry skin often feels tight after washing.", "• Oily skin looks shiny within a few hours.", "• Combination skin is oily in the middle of the face and drier on the cheeks.", "• Sensitive skin stings or turns red easily.", "Skin changes with season, age and stress, so treat these as rough guides."),
            s("Be patient", "Skin renews itself over weeks, not days. Give a new routine 4 to 8 weeks before judging it, unless it stings or irritates, in which case stop.")),
        a("cleanser", "Skincare", "Cleansing",
            "Wash gently, with lukewarm water, once or twice a day.",
            s("How", "Wet your face with lukewarm water, massage a small amount of gentle cleanser for about 30 seconds, rinse well and pat dry with a clean towel."),
            s("Common mistakes", "• Scrubbing hard. It does not clean better and can damage the skin barrier.", "• Very hot water, which can dry the skin.", "• Washing many times a day. Twice is plenty; after sweating, one extra rinse is fine."),
            s("Good to know", "If your skin feels tight, squeaky or stings after washing, the cleanser is probably too harsh.")),
        a("moisturizer", "Skincare", "Moisturiser",
            "Moisturiser helps skin hold water. Oily skin needs it too, just a lighter kind.",
            s("What it does", "A moisturiser holds water in the skin and supports its protective barrier. It will not change your skin type."),
            s("Choosing one", "• Dry skin: richer creams.", "• Oily or acne-prone skin: light gel or lotion labelled non-comedogenic (a claim that is not strictly regulated, so try it and see).", "• Sensitive skin: fewer ingredients and no added fragrance."),
            s("How", "Apply to slightly damp skin after washing, in a thin layer.")),
        a("sunscreen", "Skincare", "Sunscreen",
            "Daily sun protection is the most evidence-backed step for healthy-looking skin.",
            s("What to look for", "Broad-spectrum sunscreen with SPF 30 or higher. Choose a texture you will actually wear every day."),
            s("How much and how often", "Use a generous amount: about two finger-lengths for the face and neck. Apply it as the last step of your morning routine, and re-apply roughly every two hours if you are outdoors, sweating or swimming."),
            s("Also", "UV is present on cloudy days. Shade, a hat and sunglasses all help.")),
        a("actives", "Skincare", "Active ingredients: go slowly",
            "Retinoids, acids and vitamin C can help, but they can also irritate. Add one at a time.",
            s("The rule", "Introduce one new active at a time, use it a couple of times a week at first, and wait a few weeks before adding anything else."),
            s("Things to expect", "• Retinoids (like retinol, adapalene): can cause dryness and peeling at first. Use at night, always use sunscreen the next day.", "• Exfoliating acids (glycolic, lactic, salicylic): can sting if overused.", "• Benzoyl peroxide: can bleach towels and clothing, and dry the skin.", "• Vitamin C: can tingle; store it away from light."),
            s("Pregnancy and medical conditions", "If you are pregnant, planning a pregnancy, or have a skin condition, ask a doctor before using retinoids or other strong actives."),
            s("Stop if", "Stop a product if it burns, causes swelling, blisters or a spreading rash.")),
        a("patch_test", "Skincare", "How to patch test",
            "Test a new product on a small area before it goes on your face.",
            s("Steps", "• Wash and dry a small patch of skin behind your ear or on your inner forearm.", "• Apply a small amount of the product.", "• Leave it for 24 hours without washing the spot, and look again at 48 hours.", "• If you see redness, itching, swelling or bumps, rinse it off and do not use it."),
            s("Limits", "A patch test cannot predict everything, but it catches many reactions early. Note any reaction in the product reaction log.")),
        a("acne_info", "Skincare", "Understanding acne",
            "Acne is common and is not caused by a dirty face.",
            s("What it is", "Acne starts when pores get blocked by oil and dead skin and sometimes become inflamed. Hormones, genetics and stress can all play a role."),
            s("What can help", "• Gentle cleansing, not scrubbing.", "• Over-the-counter products with benzoyl peroxide, salicylic acid or adapalene, introduced slowly.", "• Avoiding picking and squeezing, which raises the chance of marks and scars.", "• Giving any routine 8 to 12 weeks."),
            s("See a doctor or skin specialist if", "• It is painful, deep or cyst-like.", "• It leaves scars or dark marks.", "• It is affecting your mood or confidence.", "• Products from the pharmacy have not helped after 8 to 12 weeks.", "There are effective prescription treatments, and getting help early can limit scarring.")),
        a("when_see_pro", "Skincare", "When to see a professional",
            "Some skin and hair changes should be looked at by a doctor, not fixed with products.",
            s("Get it checked", "• A mole that changes in size, shape or colour, bleeds, or looks different from your other moles.", "• A sore that does not heal in a few weeks.", "• A rash that spreads, blisters, or comes with fever.", "• Signs of infection: increasing pain, warmth, pus.", "• Sudden or patchy hair loss.", "• Severe or painful acne, or acne with scarring."),
            s("Why", "Many conditions look similar. A doctor can tell them apart; an app cannot.")),

        // ------------------------------------------------------------ hair
        a("hair_care_basics", "Hair", "Hair care basics",
            "Wash for your scalp, condition for your hair, and be gentle when wet.",
            s("Washing", "Shampoo is mainly for the scalp. Oily scalps may need washing every day or two; dry or curly hair often does better with fewer washes."),
            s("Conditioning", "Apply conditioner mainly to the lengths and ends, then rinse."),
            s("Be gentle", "Wet hair is fragile. Use a wide-tooth comb, pat dry instead of rubbing hard, and go easy on heat styling."),
            s("Dandruff", "Flakes are common. An anti-dandruff shampoo (with ingredients like zinc pyrithione or ketoconazole) used as directed often helps. If your scalp is very red, sore or the flakes persist, see a doctor.")),
        a("hair_lengths", "Hair", "Choosing a hairstyle",
            "Start from your hair type and how much time you want to spend, not from a rule about face shapes.",
            s("A practical way to choose", "• Your hair type: straight, wavy, curly or coily hair behaves differently with each cut.", "• How much time you will spend on it each morning.", "• Your hairline and growth patterns, which a barber can see better than you can.", "• What you feel comfortable in, which matters more than any chart."),
            s("About face-shape guides", "Face-shape advice is a rule of thumb, not a rule. No cut is the best for every face, and many suit-everyone claims online are opinions. Treat them as ideas to try."),
            s("Talking to a barber or stylist", "• Bring two or three photos of styles you like.", "• Say how long you will spend styling.", "• Ask how to style it at home and how often to get it trimmed.", "• Hair grows back, so a cut you do not like is temporary.")),
        a("hair_loss_info", "Hair", "Hair shedding and hair loss",
            "Some shedding is normal. Patterned thinning is common and mostly genetic.",
            s("Normal shedding", "Most people lose roughly 50 to 100 hairs a day."),
            s("Common patterns", "• Gradual thinning at the temples or crown is often inherited.", "• A noticeable increase in shedding a few months after illness, stress or crash dieting is common and often settles.", "• Round, smooth patches of loss need a doctor's opinion."),
            s("Treatments", "Some medicines can slow hair loss in some people, but they have side effects and are not for everyone. Talk to a doctor before starting any, and be wary of products promising to regrow hair quickly."),
            s("See a doctor if", "Hair loss is sudden, patchy, comes with a sore or scaly scalp, or you are worried.")),
        a("facial_hair", "Hair", "Facial hair and shaving",
            "How thick facial hair grows is mostly genetics. How you look after it is up to you.",
            s("Growing a beard", "Patchiness is common and often fills in over a few months. Keep the neck and cheek lines tidy, wash it, and moisturise the skin underneath."),
            s("Shaving", "• Use a sharp, clean blade and replace it often.", "• Soften with warm water first and use shaving cream or gel.", "• Shave with the direction of hair growth to start with.", "• Do not press hard. Rinse and pat dry, then moisturise."),
            s("Ingrown hairs and razor bumps", "These are more common with curly hair. Shave less closely, avoid pulling the skin tight, and do not pick. If they become painful or infected, see a doctor.")),

        // ------------------------------------------------------------ hygiene
        a("hygiene_basics", "Hygiene", "Everyday hygiene",
            "Small daily habits that keep you comfortable and healthy.",
            s("Daily", "• Shower or wash, paying attention to underarms, groin, and feet, and dry well.", "• Brush your teeth for two minutes, twice a day, and clean between your teeth once a day.", "• Wear clean clothes and clean socks.", "• Use deodorant or antiperspirant if you like."),
            s("Regularly", "• Wash your hands before eating and after the toilet.", "• Trim fingernails and toenails.", "• Wash pillowcases and towels often.", "• Change razor blades regularly."),
            s("Feet", "Dry between the toes and rotate shoes. If you have itching, peeling or a persistent smell, an athlete's foot treatment or a doctor visit may be needed.")),
        a("ingredients", "Hygiene", "Reading product labels",
            "Marketing claims are not the same as evidence. The ingredient list is more useful.",
            s("Tips", "• Ingredients are listed from the highest amount to the lowest.", "• Terms such as 'natural', 'clean' or 'dermatologist-tested' have no fixed meaning for how well something will suit you.", "• Fragrance and essential oils are common causes of irritation.", "• Price and quality are not the same thing."),
            s("Try one thing at a time", "If you start several products at once and your skin reacts, you will not know which one caused it."),
            s("See also", "The ingredient glossary in the Grooming section.")),

        // ------------------------------------------------------------ posture
        a("posture_myths", "Posture", "Posture: what is helpful",
            "There is no single perfect posture. Moving often matters more than holding one position.",
            s("The best posture is the next one", "Staying in any position for a long time gets uncomfortable. Change position, stand up and walk around every 30 to 60 minutes."),
            s("What may help", "• Strengthening your upper back and core.", "• Stretching tight chest and hip muscles.", "• Setting your screen near eye level.", "• Short posture breaks during the day."),
            s("What to be cautious about", "• Over-correcting into a stiff stance.", "• Posture braces and gadgets that promise to fix everything.", "• Being told that slouching damages your spine. Evidence for that is weak.", "Pain, numbness or tingling should be checked by a doctor or physiotherapist.")),
        a("mobility_safety", "Posture", "Safe stretching and mobility",
            "Stretch to a point of gentle tension, never to pain.",
            s("Rules of thumb", "• Warm up first, even with a short walk.", "• Go slowly and breathe normally.", "• Do not bounce.", "• Stop if you feel sharp pain, pinching, tingling or numbness."),
            s("Flexibility is gradual", "Flexibility changes over weeks. Forcing a stretch does not speed it up and can cause injury."),
            s("Existing injuries", "If you have an injury, a long-term condition or have had surgery, check with a doctor or physiotherapist before starting.")),

        // ------------------------------------------------------------ height
        a("height_facts", "Height & growth", "What affects height",
            "Height is largely set by genetics. Nothing you do as an adult can add bone length.",
            s("What decides height", "Genetics is the biggest factor. Nutrition, general health and sleep during childhood and the teenage years also matter."),
            s("Growth plates", "Bones grow at areas called growth plates. These close in the late teens (on average a little earlier in girls than boys, and it varies a lot). After they close, height cannot be increased by stretching, hanging, exercise, supplements or devices."),
            s("What you may see", "• Standing and sitting tall can make you look a little taller, but only by a small amount.", "• You are slightly taller in the morning than at night, by about 1 to 2 cm, because the spine compresses during the day.", "• Measurements can differ by about half a centimetre or more depending on how you stand."),
            s("Watch out for", "Ads promising to make you taller. Extreme claims about pills, creams, hanging or special exercises have no good evidence. Surgical and hormone options exist only for particular medical situations and carry real risks."),
            s("If you are worried", "If you are still growing and concerned about your growth, see a doctor. They can check growth charts and rule out medical causes.")),
        a("measure_height", "Height & growth", "How to measure your height",
            "A repeatable method matters more than any single reading.",
            s("Method", "• Take off shoes and stand on a hard, flat floor against a flat wall.", "• Heels, bottom, shoulder blades and the back of the head lightly touch the wall.", "• Look straight ahead with the chin level.", "• Ask someone to place a flat object (like a book) on your head against the wall and mark the spot.", "• Measure from the floor to the mark."),
            s("Be consistent", "Measure at about the same time of day, since height changes slightly through the day. Take two or three readings and use the middle one."),
            s("Reading the numbers", "Differences of under a centimetre between measurements are usually just measurement error. Do not read a trend into one or two readings.")),

        // ------------------------------------------------------------ sleep
        a("growth_how", "Height & growth", "How growth works, from childhood to the late teens",
            "Bones lengthen at growth plates. Growth is steady, speeds up in puberty, then stops.",
            s("The short version", "Height comes from bones getting longer. In children and teens this happens at the growth plates: areas of cartilage near the ends of the long bones, mostly in the legs and spine. Growth is steady in childhood, speeds up during the puberty growth spurt, then slows and stops once the plates harden and close.", "How sure is this? Established."),
            s("Typical timing (with wide variation)", "• Between early childhood and puberty, children typically grow about 5 to 6 cm a year.", "• At the fastest point of the puberty spurt, typically about 8 to 10 cm a year.", "• In girls the spurt usually starts around 9 to 12 and peaks around 11 to 12. In boys it usually starts around 11 to 14 and peaks around 13 to 14.", "• Most girls are close to their adult height by about 15 to 16. Many boys keep growing slowly until about 17 to 18, and some a little longer.", "These are typical figures, not targets. Healthy people start and finish earlier or later."),
            s("After growth stops", "Once the growth plates have closed, bones cannot get longer. That is why this app does not promise to make anyone taller: there is nothing a stretch, food or supplement can do to lengthen adult bones."),
            s("What a tracker can and cannot do", "Recording your own height every few months shows your own pattern over time. It cannot tell you whether your growth is normal, or predict how tall you will be. A doctor can compare your measurements with growth charts.")),
        a("growth_genes_puberty", "Height & growth", "Genes, puberty timing and why everyone is different",
            "Genes explain most of the differences in adult height. Puberty timing varies a lot.",
            s("Genes do most of the work", "Studies of families and twins find that genes explain most of the differences in adult height between people in well-fed populations, often estimated at around 80 percent. The rest comes from nutrition, health and environment. Parents' heights are a rough guide, not a promise: brothers and sisters can end up quite different.", "How sure is this? Established."),
            s("Puberty timing varies", "Starting puberty earlier or later inside the normal range is common and often runs in families. Early developers are often taller than classmates for a while, and others catch up. Later developers usually grow for longer, so being the shortest in the class at 13 says little about adult height.", "How sure is this? Established."),
            s("Comparing yourself", "Comparing with friends at one moment is misleading, because each person's growth spurt comes at a different time. Your own readings over months, taken the same way, tell you far more."),
            s("Worried?", "If you are concerned about your growth or puberty, a doctor can help. See \"When to talk to a doctor\".")),
        a("growth_basics", "Height & growth", "Sleep, food and movement: the basics that support healthy growth",
            "They help you grow the way your genes allow. They do not add height beyond that.",
            s("The key idea", "Growth needs enough energy, nutrients, sleep and general health. Long-term undernutrition or an untreated illness can slow growth, and treating the cause can help a young person grow as their genes allow. That is different from growing taller than their genes allow, which nothing is known to do.", "How sure is this? Established."),
            s("Eating", "• Regular meals with a mix of foods: protein (eggs, dairy, pulses, fish, meat or alternatives), fruit and vegetables, whole grains.", "• Calcium sources (dairy or fortified alternatives, leafy greens) and vitamin D (daylight, fortified foods, or as your doctor advises).", "• Skipping meals, crash diets or cutting out whole food groups while you are still growing is risky. If food or weight worries you, talk to a doctor or a trusted adult.", "No single food makes you taller than your genes allow."),
            s("Sleep", "Teens are generally advised to get about 8 to 10 hours of sleep a night. Growth hormone is released in pulses, with a large one in deep sleep, which is one reason regular sleep matters. Getting enough, regularly, is the goal: more than you need has not been shown to add height.", "How sure is this? Established for needing enough sleep. Uncertain for any extra benefit."),
            s("Movement", "Regular activity supports strong bones, muscles and mood. Weight-bearing activities such as running, jumping and sports help build bone in the teen years. Strength training with good technique and sensible supervision is considered safe for teens and does not stunt growth. Extreme training, or a body weight that is too low, can harm growth and health.", "How sure is this? Established."),
            s("Supplements", "Most young people who eat a varied diet do not need height or growth supplements. Ask a doctor before taking any supplement, because high doses of some vitamins and minerals are harmful.")),
        a("growth_posture_bone", "Height & growth", "Posture is not the same as bone growth",
            "Standing tall can show your full height. It does not make bones longer.",
            s("Two different things", "Bone growth happens at the growth plates and sets how tall you can be. Posture is how you hold your body, using muscles and joints. They are separate."),
            s("What posture can change", "Slouching can make you look and measure a little shorter, often by up to a centimetre or two, because the curves of the spine are exaggerated. Standing tall brings you back to your full measured height. It does not add to it. Your spine's discs also compress a little through the day, so most people are slightly shorter in the evening than in the morning.", "How sure is this? Established."),
            s("Slouching does not stunt growth", "There is no good evidence that sitting badly or carrying a school bag stops bones from growing. Comfort still matters: long periods in one position, or a heavy bag on one shoulder, can leave you stiff or sore. Change position often and keep bags light, carried on both shoulders."),
            s("What helps", "• Move often: change position every 30 to 60 minutes.", "• Build gentle strength in the back and core, and keep shoulders and hips moving. The posture and mobility library has beginner-friendly options.", "• Set a screen at about eye level."),
            s("Not a treatment", "These activities do not lengthen bones or treat scoliosis or other spine conditions. If one shoulder or hip looks higher, you can see a curve, or you have back pain that keeps coming back, see a doctor.")),
        a("growth_claims", "Height & growth", "Claims to be careful about",
            "What the evidence says about stretching, pills, hormones and other height claims.",
            s("Stretching or hanging", "Stretching can improve flexibility and feel good. It can also ease disc compression for a while, so you may measure slightly taller for a few hours. There is no evidence that it lengthens bones permanently.", "Verdict: partly true, mostly misleading."),
            s("Height pills and supplements", "No supplement has been shown to make healthy people taller. Some products contain ingredients that are not on the label, and high doses of some vitamins and minerals are harmful.", "Verdict: not supported. Ask a doctor first."),
            s("Growth hormone", "Prescribed by specialists for particular medical conditions, with monitoring. Using it without a medical reason, or from an unregulated source, can cause serious harm. Never use hormones without medical supervision.", "Verdict: medical treatment only, not a shortcut."),
            s("Lifting weights or caffeine stunt growth", "There is no good evidence for either. Supervised strength training is considered safe for teens. Caffeine in small amounts has not been shown to affect height, but a lot of it can ruin sleep, and sleep matters.", "Verdict: myths."),
            s("Special shoes and insoles", "They make your shoes taller, not you.", "Verdict: true but not what it sounds like."),
            s("Surgery", "Limb-lengthening is a major medical procedure used for specific conditions, with real risks and a long recovery. It is not a cosmetic shortcut and is outside what this app covers."),
            s("How to judge a claim", "• Who earns money if you believe it?", "• Was it tested on people like you, with a comparison group?", "• Does it promise an exact number of centimetres? Honest sources do not.", "• Before-and-after photos prove very little: lighting, posture and time of day change how tall someone looks.")),
        a("growth_doctor", "Height & growth", "When to talk to a doctor",
            "Signs that are worth a check, and what a doctor can do that an app cannot.",
            s("Worth a conversation", "• You seem to be growing much more slowly than before, or have not grown for a year or more while still young.", "• No signs of puberty starting by about 13 for girls or 14 for boys, or puberty starting very early (before about 8 for girls or 9 for boys).", "• You are a lot shorter or taller than others your age and than your parents' heights would suggest.", "• Back or bone pain that keeps coming back, one shoulder or hip clearly higher, a visible curve in the spine, or limping.", "• Feeling unwell, very tired, or losing or gaining weight without trying, along with slow growth."),
            s("What a doctor can do", "A doctor can measure you accurately, plot your height on a growth chart for your age, ask about family heights, check how far puberty has progressed, and arrange tests such as a bone-age X-ray if needed. Very often the answer is reassurance that growth is normal for you."),
            s("Using your records", "You can bring the dates and readings from this app and say how you measured. Self-measured readings are less accurate than clinic measurements, and this app cannot tell you whether your growth is normal."),
            s("It is normal to ask", "Asking about growth or puberty is common and nothing to be embarrassed about. A parent, school nurse or family doctor is a good first person to talk to.")),
        a("sleep_basics", "Sleep", "Sleep basics",
            "Regular, enough sleep supports energy, mood, learning and recovery.",
            s("How much", "Most teenagers do best with about 8 to 10 hours, and most adults with 7 to 9. Needs vary between people."),
            s("What helps", "• A regular wake-up time, including on weekends.", "• A dark, quiet and cool room.", "• Daylight and movement during the day.", "• Caffeine earlier in the day, not in the evening."),
            s("See a doctor if", "You snore loudly, stop breathing in your sleep, always feel exhausted despite enough hours, or have trouble sleeping for weeks.")),
        a("wind_down", "Sleep", "A simple wind-down routine",
            "Doing the same calm things in the same order tells your body it is time to sleep.",
            s("Ideas", "• Dim the lights about an hour before bed.", "• Put your phone away or on a quiet mode and charge it across the room.", "• Do your evening skincare and brush your teeth.", "• Read a few pages, stretch gently or do slow breathing.", "• Write tomorrow's top three tasks so you are not thinking about them in bed."),
            s("If you cannot sleep", "Do not stay in bed frustrated. Get up, do something quiet in low light, and come back when sleepy.")),

        // ------------------------------------------------------------ nutrition
        a("eating_basics", "Nutrition", "Eating well, without strict rules",
            "Regular meals with a mix of foods work better than strict diets.",
            s("A simple plate", "Aim for a mix: some protein, some vegetables or fruit, some whole grains or other carbohydrates, and some healthy fats."),
            s("Easy examples", "• Protein: dal, rajma, chana, paneer, curd, eggs, soy, fish, chicken, tofu.", "• Carbohydrates: roti, rice, poha, oats, millets, potatoes.", "• Vegetables and fruit: seasonal ones, as many colours as you can.", "• Fats: nuts, seeds, mustard or groundnut oil in moderation."),
            s("Avoid", "• Skipping meals regularly.", "• Crash diets and extreme restrictions.", "• Treating foods as 'good' or 'bad'. All foods can fit."),
            s("If food worries you", "If you find yourself preoccupied with food, weight or your body, or you restrict, binge or purge, talk to a doctor or a trusted adult. That is not a failure, and help works.")),
        a("hydration", "Nutrition", "Drinking enough water",
            "There is no one magic number. Drink regularly and let thirst and urine colour guide you.",
            s("Rough guide", "Pale yellow urine usually means you are well hydrated. Dark yellow suggests you need more fluid."),
            s("Practical tips", "• Keep a bottle with you.", "• Drink a glass with each meal.", "• Drink more in hot weather and when exercising.", "• Water, milk, buttermilk, coconut water and unsweetened tea all count."),
            s("Note", "If you have a medical condition affecting kidneys or heart, follow your doctor's advice about fluids.")),

        // ------------------------------------------------------------ habits
        a("habit_science", "Habits", "How habits stick",
            "Make it small, tie it to something you already do, and do not let one miss become two.",
            s("Start tiny", "A habit you can finish in two minutes is more likely to continue than a big one you dread. You can always do more."),
            s("Attach it", "Link a new habit to an existing one: after brushing my teeth, I will do my skincare."),
            s("A miss is not failure", "Everyone misses days. The useful rule is to avoid missing twice in a row. In this app a missed day ends your current run but your best run and your history stay."),
            s("It takes a while", "Studies suggest the time to form a habit varies widely, from a few weeks to many months, depending on the person and the habit."),
            s("Track simply", "Ticking off a habit each day is enough. Too many habits at once is the most common reason people give up.")),

        // ------------------------------------------------------------ training
        a("training_basics", "Training basics", "Getting started with home workouts",
            "A few short sessions a week, done with good form, will take you further than occasional hard sessions.",
            s("Before you start", "If you have a health condition, an injury, or have been inactive for a long time, check with a doctor first."),
            s("The basics", "• Warm up for a few minutes.", "• Choose form over speed and number of repetitions.", "• Two or three sessions a week is a good start.", "• Rest days are part of training.", "• If an exercise hurts (joint or sharp pain), stop and choose an easier version."),
            s("Progressing", "When a set feels easy, add a rep or two, then a set, then move to a harder variation. Change one thing at a time."),
            s("How hard?", "You should feel worked but able to speak in short sentences. If you cannot talk at all or feel dizzy, stop and rest.")),
        a("rest_recovery", "Training basics", "Rest and recovery",
            "Your body adapts to training while you rest.",
            s("Soreness", "Mild muscle soreness 1 to 3 days after a new exercise is common. Sharp or joint pain, swelling or numbness is not normal."),
            s("What helps", "• Enough sleep.", "• Regular meals and fluids.", "• Easy movement like walking or the gentle recovery plan.", "• At least one or two rest days a week."),
            s("Take a lighter week", "If you feel unusually tired or sore, take a lighter or rest week. It will not undo your progress."))
    )

    val glossary: List<Ingredient> = listOf(
        Ingredient("Glycerin", "A humectant that draws water into the skin. Found in many moisturisers.", "Rarely irritating."),
        Ingredient("Hyaluronic acid", "A humectant that holds water on the skin's surface.", "Works best on slightly damp skin."),
        Ingredient("Ceramides", "Lipids that are part of the skin's natural barrier. Used in barrier-support creams.", "Generally well tolerated."),
        Ingredient("Niacinamide", "A form of vitamin B3, used for oil balance and skin barrier support.", "Some people find high concentrations irritating; start low."),
        Ingredient("Salicylic acid (BHA)", "An exfoliating acid that can get into pores. Used for blackheads and acne.", "Can dry or sting. Not for use in pregnancy without asking a doctor."),
        Ingredient("Glycolic and lactic acid (AHA)", "Exfoliating acids that loosen dead skin cells.", "Raises sun sensitivity. Use sunscreen and start slowly."),
        Ingredient("Benzoyl peroxide", "An acne ingredient that reduces acne-causing bacteria.", "Can dry skin and bleach fabrics."),
        Ingredient("Retinol and other retinoids", "Vitamin A derivatives used for acne and signs of ageing.", "Can cause dryness and peeling. Ask a doctor if pregnant or planning a pregnancy."),
        Ingredient("Vitamin C (ascorbic acid)", "An antioxidant used in the morning under sunscreen.", "Can tingle, and it breaks down with light and air."),
        Ingredient("Zinc oxide and titanium dioxide", "Mineral sunscreen filters that sit on top of the skin.", "May leave a white cast."),
        Ingredient("Panthenol (pro-vitamin B5)", "A soothing ingredient used in moisturisers and hair products.", "Rarely irritating."),
        Ingredient("Fragrance / parfum", "Added to make products smell nice.", "A common cause of irritation and allergy. Choose fragrance-free if your skin is sensitive."),
        Ingredient("Alcohol denat. (drying alcohol)", "Used to make products feel light and dry fast.", "Can be drying in large amounts. Fatty alcohols (like cetyl alcohol) are different and are moisturising.")
    )

    private val byKey = all.associateBy { it.key }
    fun get(key: String): Article? = byKey[key]
    fun inGroup(g: String) = all.filter { it.group == g }

    fun search(q: String): List<Article> {
        val s = q.trim().lowercase()
        if (s.length < 2) return emptyList()
        return all.filter { it.title.lowercase().contains(s) || it.summary.lowercase().contains(s) || it.group.lowercase().contains(s) }
    }
}

/** One-tap starter habits offered on an empty Habits screen. Nothing is added without a tap. */
class StarterHabit(val name: String, val descr: String, val category: String, val slot: String, val freq: String, val days: Int, val method: Int, val target: String)

object Starters {
    val all = listOf(
        StarterHabit("Drink water", "Drink a glass of water with each meal.", "Health", "anytime", "daily", 127, 0, "4 glasses"),
        StarterHabit("Morning skincare", "Cleanse, moisturise, sunscreen.", "Skincare", "morning", "daily", 127, 0, ""),
        StarterHabit("Evening skincare", "Cleanse and moisturise before bed.", "Skincare", "evening", "daily", 127, 0, ""),
        StarterHabit("Move for 10 minutes", "A walk or any easy movement.", "Fitness", "anytime", "daily", 127, 0, "10 min"),
        StarterHabit("Posture break", "Stand up, roll your shoulders, reset your sitting position.", "Posture", "anytime", "weekdays", 31, 0, ""),
        StarterHabit("Bedtime routine", "Start winding down at your set time.", "Sleep", "evening", "daily", 127, 0, ""),
        StarterHabit("Read for 10 minutes", "Any book, any topic.", "Learning", "evening", "daily", 127, 0, "10 min"),
        StarterHabit("Mobility session", "About 10 minutes of gentle moves from the posture and mobility library.", "Posture", "anytime", "custom", 21, 0, "10 min"),
        StarterHabit("Regular sleep time", "Go to bed and get up at about the same times.", "Sleep", "evening", "daily", 127, 0, ""),
        StarterHabit("Regular balanced meals", "Eat regular meals with a mix of foods. No strict rules.", "Nutrition", "anytime", "daily", 127, 0, ""),
        StarterHabit("Complete a planned workout", "Counts automatically when you finish a workout in this app.", "Fitness", "anytime", "custom", 21, 1, "")
    )
}
