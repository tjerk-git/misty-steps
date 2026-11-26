# R8 keep rules for Misty Steps. Keep this file short: every broad rule (-keep class com.foo.**) switches shrinking,
# optimization and obfuscation off for that whole package, which is what Play vitals "DEX code optimization" measures.
# Libraries (MapLibre, Compose, WorkManager, Play Billing) ship their own consumer rules; add a rule here only for a crash
# that R8 causes, with the reason in a comment, and make it as narrow as possible.
