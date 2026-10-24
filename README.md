# SoundFlow Mobile

Portage Android de [SoundFlow](https://github.com/raphaelhelena1-art/Soundflow) (version Windows).
Lecteur audio/vidéo type Spotify avec téléchargement intégré depuis YouTube, sans publicité,
sans API officielle. Voir [`SPEC.md`](./SPEC.md) pour la spécification fonctionnelle complète.

## État actuel (étape 3+ — extraction, recherche, et gros ajout de fonctionnalités)

Ce qui est en place :
- Structure Gradle multi-module : `app` (UI Compose) + `core` (logique métier)
- Thème Compose sombre + vert
- Navigation avec les 5 onglets
- **Lecteur audio fonctionnel** (Media3/ExoPlayer + service en arrière-plan + mini-lecteur persistant),
  avec remontée des erreurs de lecture (flux inaccessible, etc.) visible dans l'UI
- **Recherche YouTube fonctionnelle** (NewPipeExtractor v0.26.2, sans API officielle, sans quota) :
  recherche en temps réel avec suggestions d'autocomplétion, résultats avec vignettes, lecture
  au tap (avec repli sur flux HLS si le flux direct est indisponible)
- **Bibliothèque (historique d'écoute)** fonctionnelle — les 30 derniers morceaux écoutés,
  tap pour rejouer (session uniquement pour l'instant, pas encore persisté)
- **Paramètres fonctionnels** (onglet Profil) :
  - Minuteur de sommeil (réel — met en pause après le délai choisi)
  - Égaliseur (réel — plat / bass boost / aigus renforcés / vocal, via `android.media.audiofx.Equalizer`)
  - Langue FR/EN/JA (change la langue de l'app)
  - Numéro de version (réel, lu depuis `BuildConfig`)
  - Réinitialisation complète (avec confirmation)
  - Crossfade : curseur présent mais pas encore câblé au vrai moteur audio (voir TODO dans le code)

Ce qui n'est PAS encore fait (prochaines étapes, voir `SPEC.md`) :
- Téléchargements (WorkManager, stockage local, choix qualité) — onglet encore un placeholder
- Playlists (création, types en ligne/hors-ligne, réorganisation), Favoris
- Historique persistant (actuellement en mémoire, perdu au redémarrage de l'app)
- Statistiques d'écoute (temps par jour/mois/année, artistes les plus écoutés)
- Paroles synchronisées (LRCLIB), vrai crossfade audio
- Pas de logo/icône définitif (à récupérer depuis le repo Windows)

## Ouvrir le projet

1. Ouvrir ce dossier avec **Android Studio** (version récente, ex. Koala ou plus récent)
2. Android Studio va proposer de générer/synchroniser le wrapper Gradle automatiquement —
   accepter. (Le fichier `gradle-wrapper.jar` binaire n'est pas inclus dans cette génération
   initiale ; Android Studio le régénère à l'ouverture.)
3. Laisser Gradle synchroniser les dépendances (nécessite une connexion internet, notamment pour
   JitPack qui héberge NewPipeExtractor)
4. Lancer sur un émulateur ou un appareil (Android 8.0+ / API 26+)

## Structure

```
SoundFlow/
├── app/                    UI (Jetpack Compose), navigation, thème
│   └── ui/screens/         Un fichier par onglet
├── core/                   Logique métier, indépendante de l'UI
│   └── (extraction, download, player, data, stats, lyrics — vides pour l'instant)
└── SPEC.md                 Spécification fonctionnelle complète
```

La séparation `app` / `core` est volontaire : elle permettra de convertir `core` en module
Kotlin Multiplatform si un portage iOS est envisagé plus tard, sans toucher à l'UI Android.

## Prochaine étape

Étape 2 du plan : rendre le lecteur fonctionnel avec Media3/ExoPlayer (lecture d'un fichier
local pour valider le pipeline audio de bout en bout).
