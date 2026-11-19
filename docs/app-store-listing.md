# App Store Connect: texts for version 1.0.0 (Netherlands)

Paste these into https://appstoreconnect.apple.com (Misty Steps, iOS App, version 1.0). Character limits are Apple's.

## App Information (not per version)
- Name (30): `Misty Steps`
- Subtitle (30): `Walk and lift the mist` (EN) / `Wandel en til de mist op` (NL)
- Category: Primary Health & Fitness, secondary Travel (or Navigation)
- Content rights: no third-party content that needs rights (map data is OpenStreetMap, credited in the app)
- Age rating: answer "None" to every question (no user content, no web access beyond downloading maps, no gambling): result 4+
- Price: Free. Availability: Netherlands only.

## English (Netherlands) / primary

**Promotional text (170, can change any time)**
Your neighbourhood starts hidden in mist. Walk, and the mist lifts. No accounts, no scores, no ads: just you and the map of where you live.

**Description (4000)**
Misty Steps turns the streets around you into a map you uncover by walking.

Everything starts hidden in drifting mist. Tap Start wandering, go outside, and the mist lifts wherever you walk. Over weeks and months your own map of the place you live takes shape, one street at a time.

What you get
- A calm fog-of-war map of the Netherlands that clears as you walk
- A little pixel-art companion walking with you: a cat, a dog, an otter, a capybara and more
- Pins for places you want to remember: a bench, a view, a loud crow, a friendly cat
- A postcard of every walk to save or share
- Stats about your own map: how much of your city, the country and the world you have cleared
- Offline maps: download the provinces you walk in and the map works without a connection

Private by design
- No account, no sign-in, no friends list
- Your walks, pins and settings stay on your phone
- Your location is used on the phone only and never sent anywhere
- No ads and no tracking
- An optional, anonymous open count, off until you say yes
- Back up your map to a file you keep yourself

Free, and it stays free. All twelve provinces are free to download. If Misty Steps gets you out of the door, you are welcome to support it, but nothing is locked.

Misty Steps is made by one person in Leeuwarden. Maps come from OpenStreetMap contributors.

**Keywords (100, comma separated, no spaces after commas)**
`walking,fog of war,map,explore,neighbourhood,offline map,steps,discover,stroll,hike,city,exploration`
(that is 99 characters; Apple counts every character including commas)

**What's New in this version (4000)**
First version of Misty Steps for iPhone.

**Support URL:** https://potloodgum.com/misty-steps/
**Marketing URL (optional):** https://testers.potloodgum.com (change to a landing page later)
**Privacy Policy URL:** https://potloodgum.com/misty-steps/privacy/ (live once the `misty-steps-pages` branch of pgum_11ty is merged and deployed)
**Copyright:** `2026 Tjerk Dijkstra` (your legal name or trading name)

## Nederlands (Netherlands)

**Subtitel (30):** `Wandel en til de mist op`

**Promotietekst (170)**
Je buurt begint verborgen in de mist. Loop, en de mist trekt weg. Geen accounts, geen scores, geen reclame: alleen jij en de kaart van waar je woont.

**Beschrijving (4000)**
Misty Steps verandert de straten om je heen in een kaart die je ontdekt door te lopen.

Alles begint verborgen in drijvende mist. Tik op Start wandelen, ga naar buiten, en de mist trekt weg waar je loopt. Na weken en maanden ontstaat je eigen kaart van de plek waar je woont, straat voor straat.

Wat je krijgt
- Een rustige mistkaart van Nederland die opklaart terwijl je loopt
- Een pixelart-maatje dat met je meeloopt: een kat, een hond, een otter, een capibara en meer
- Spelden voor plekken die je wilt onthouden: een bankje, een uitzicht, een luide kraai, een vriendelijke kat
- Een ansichtkaart van elke wandeling om te bewaren of te delen
- Cijfers over je eigen kaart: hoeveel van je stad, het land en de wereld je hebt opgeklaard
- Offline kaarten: download de provincies waar je loopt en de kaart werkt zonder verbinding

Privé door ontwerp
- Geen account, geen inloggen, geen vriendenlijst
- Je wandelingen, spelden en instellingen blijven op je telefoon
- Je locatie wordt alleen op je telefoon gebruikt en nergens naartoe gestuurd
- Geen reclame en geen tracking
- Een optionele, anonieme telling van het aantal keer openen, uit tot je ja zegt
- Maak een back-up van je kaart in een bestand dat je zelf bewaart

Gratis, en dat blijft zo. Alle twaalf provincies zijn gratis te downloaden. Helpt Misty Steps je de deur uit, dan mag je het steunen, maar er zit niets op slot.

Misty Steps is gemaakt door één persoon in Leeuwarden. Kaarten komen van OpenStreetMap-bijdragers.

**Zoekwoorden (100)**
`wandelen,mist,kaart,ontdekken,buurt,offline kaart,stappen,wandeling,stad,verkennen,fog of war`

**Wat is er nieuw**
De eerste versie van Misty Steps voor iPhone.

## App Review Information
- Sign-in required: No
- Contact: your name, phone number and email (Apple needs them; not public)
- Notes for the reviewer:
  "Misty Steps is a walking app. No account is needed. On first launch it explains the app, asks (optionally) about an anonymous open count, then offers to download a province map (free, 26 to 109 MB, from maps.potloodgum.com). Choose any province, e.g. Friesland. Tap 'Start wandering' and allow location 'While Using the App'; the fog clears around your position as you move. To see it without walking, use Features > Location in the Simulator, or walk outside. Location is used only on the device. The app is for the Netherlands; positions outside the Netherlands show an empty map."
- Demo account: none.

## App Privacy (nutrition label)
- DECIDED 2026-11-19: declare the optional open count. (Earlier note: the optional anonymous open count sends app version, platform and a day count to stats.potloodgum.com only if the user says yes, with no identifier. Apple's rule counts anything sent off the device and kept as collected. Safest honest answer: declare Usage Data > Other Usage Data, "Not linked to the user", "Not used for tracking", purpose Analytics. If you declare it, add the same to `iosApp/MistySteps/PrivacyInfo.xcprivacy` (NSPrivacyCollectedDataTypes) in the next build so the two match. Done in the manifest.)
- Tracking: No.

## Before submitting for review (not needed for TestFlight)
- The "Buy me a coffee" card was removed from the About page on 2026-11-19 (Apple guideline 3.1.1: in-app tips must use In-App Purchase). A tip jar can come back later as In-App Purchase.
- Privacy policy and support pages must be live URLs.
- Screenshots (see below), App Review contact details, export compliance (the app sets "no non-exempt encryption").

## Screenshots
Apple needs 1 to 10 for the largest iPhone size: 6.9-inch, 1320 x 2868 px, PNG or JPEG, no alpha. Smaller sizes are scaled from these. Files are in `docs/store-assets/ios-6.9/` (5 screens: map while walking, stats, characters, postcard, province maps).

## Needed from you
- App Review contact: enter your phone number and hello@potloodgum.com in App Store Connect (not public; not stored in this repo)
- Your decision on the privacy label (above) and on the coffee card
- A privacy policy page and a support page URL
