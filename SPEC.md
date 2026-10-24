# SoundFlow Mobile — Spécification

## Vue d'ensemble
Portage Android de SoundFlow (version Windows), lecteur audio/vidéo type Spotify avec
téléchargement intégré depuis YouTube, sans publicité, sans API officielle (donc sans quota).

- **Plateforme** : Android (structuré pour permettre un futur portage iOS)
- **Langage** : Kotlin
- **UI** : Jetpack Compose
- **Distribution** : APK direct via GitHub Releases (pas de Play Store)
- **Compte** : aucun login. Un bouton "réinitialiser" efface toutes les données locales.
- **Thème** : sombre + accent vert (identité reprise de la version Windows)
- **Langues** : Français, Anglais, Japonais

## Sources de contenu
- YouTube / YouTube Music — extraction sans API officielle (type NewPipeExtractor), sans pub
- Import de fichiers audio locaux déjà présents sur l'appareil

## Navigation principale (5 onglets)
1. **Accueil** — suggestions "top tendance", gestion état hors-ligne
2. **Recherche** — suggestions en temps réel + suggestions basées sur l'historique de recherche
3. **Bibliothèque** — historique d'écoute (onglet séparé)
4. **Téléchargements** — liste de tous les téléchargements
5. **Profil** — stats + accès aux paramètres

## Playlists
- Type choisi à la création :
  - **Hors-ligne** : uniquement des sons déjà téléchargés (impossible d'ajouter un son non téléchargé)
  - **En ligne** : tout type de son (téléchargé ou streaming)
- Actions sur les sons dans une playlist :
  - Suppression
  - Déplacement / réorganisation (ordre)
  - Modes de lecture : boucle, aléatoire, ordre de la playlist
- Pas de partage de playlists (pour l'instant)

## Téléchargements
- Qualité audio/vidéo sélectionnable dans les paramètres
- Par défaut : meilleure qualité disponible
- Mode hors-ligne complet une fois téléchargé

## Profil / Statistiques
- Temps d'écoute : jour / mois / année / total
- Artistes les plus écoutés (par période)
- Totaux : nombre de titres écoutés, nombre d'artistes, nombre de playlists

## Paramètres
- Minuteur de sommeil (15 / 30 / 60 min, etc.)
- Crossfade (durée réglable)
- Égaliseur (plat, bass boost, etc.)
- Qualité de téléchargement par défaut (audio / vidéo)
- Langue (FR / EN / JP)
- Thème (sombre + vert)
- Numéro de version
- Réinitialisation complète (suppression de toutes les données locales)

## Paroles
- Paroles synchronisées via LRCLIB (gratuit, sans clé API)

## Hors périmètre v1
- Compte / connexion / synchro cloud
- Partage de playlists

## Architecture technique
- `app` (module Android/Compose) — UI uniquement
- `core` (module Android library) — logique métier : extraction YouTube, téléchargements,
  lecteur (Media3/ExoPlayer), base de données locale (Room), stats, paroles (LRCLIB)
  → séparation pensée pour faciliter une future conversion en Kotlin Multiplatform (iOS)

## Ordre de développement
1. Setup projet (scaffolding Gradle, structure modules)
2. Socle lecture (Compose minimal + Media3/ExoPlayer fonctionnel)
3. Extraction & recherche (NewPipeExtractor, recherche YouTube, streaming)
4. Téléchargements (WorkManager, stockage local, choix qualité)
5. Bibliothèque & Playlists (historique, playlists, hors-ligne, favoris)
6. Extras (paroles LRCLIB, égaliseur, crossfade, minuteur de sommeil)
7. Habillage final (charte graphique Windows, thème sombre + vert, SVG/icônes, 3 langues)
8. Release (build APK, repo GitHub dédié, GitHub Releases, vérificateur de mise à jour)
