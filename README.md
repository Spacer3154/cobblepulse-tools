# CobblePulse Tools

Outils de CobblePulse inspirés de Pixelmon, pour Minecraft 1.21.1 (Fabric) et Cobblemon 1.8.4. Le même jar va sur le serveur et dans le modpack (les blocs sont enregistrés des deux côtés).

## Contenu

### Movement Plate
- Propulse toute entité posée dessus (joueur, mob, Pokémon, objet) dans le sens de la flèche et la recentre sur l'axe de la plaque.
- Posée, elle pointe dans la direction où regarde le joueur. Les plaques s'enchaînent ; l'élan continue un peu après la dernière plaque.
- **Boucles interdites** (anti-AFK) :
  - à la pose, une plaque qui fermerait une boucle (deux plaques face à face, carré, circuit…) est refusée avec un message ;
  - en jeu, une boucle construite autrement (`/setblock`, structure, WorldEdit, ou trou franchi grâce à l'élan) est détectée : l'entité qui repasse sur une plaque déjà traversée pendant le même trajet est arrêtée et n'est plus poussée tant qu'elle ne quitte pas les plaques (1 s hors des plaques termine un trajet).
- Pioche nécessaire pour la récupérer. Waterloggable.
- Recette : colorant vert citron, 2 redstone, lampe à redstone, lingot de fer.

### Stick Plate
- Ralentit comme le sable des âmes (on peut sauter) et arrête net une entité lancée par une Movement Plate.
- Recette : 2 sable des âmes, colorant bleu, 2 redstone, lampe à redstone, lingot de fer.

### Éditeur de Pokémon (baguette, staff)
- Clic droit sur un Pokémon (sauvage ou sorti par un joueur) : ouvre l'éditeur.
- Pokémon de l'équipe ou du PC d'un joueur en ligne : `/cptools editer <joueur> <1-6>` ou `/cptools editer <joueur> pc <boîte> <emplacement>`.
- Onglets : **Général** (espèce, forme, niveau, chromatique, genre, nature, menthe, talent, surnom), **Statistiques** (IV, EV avec total / 510), **Capacités** (4 emplacements, liste des capacités apprenables ou de toutes), **Objets & divers** (objet tenu, Poké Ball, type Téra, bonheur, niveau Dynamax, Gigamax, échangeable). Boutons Soigner, Recharger, Appliquer.
- Le serveur re-vérifie tout (permission, valeurs, Pokémon retrouvé par son UUID) et refuse l'édition d'un Pokémon en combat. Chaque modification est écrite dans le journal du serveur.
- Pas de recette : `/cptools baguette [joueur]` ou onglet créatif.

## Commandes et permission
`/cptools` (alias `/cobblepulsetools`) et la baguette demandent `cobblepulse.tools.admin` (LuckPerms). Sans LuckPerms, ou si le nœud n'est défini nulle part, il faut être opérateur de niveau 2. Un refus explicite dans LuckPerms l'emporte.

| Commande | Rôle |
|---|---|
| `/cptools baguette [joueur]` | Donne l'Éditeur de Pokémon |
| `/cptools editer <joueur> <1-6>` | Édite un Pokémon de l'équipe |
| `/cptools editer <joueur> pc <boîte> <emplacement>` | Édite un Pokémon du PC |
| `/cptools reload` | Recharge `config/cobblepulse/tools.json` |

## Configuration (`config/cobblepulse/tools.json`)
- `movement_plate_speed` (0.6) : vitesse donnée par une plaque (≈ 6,5 blocs/s). Envoyée aux clients à la connexion et au `reload`.
- `movement_plate_centering` (0.3) : rappel vers le centre de la plaque.
- `admin_permission` (`cobblepulse.tools.admin`), `op_level_fallback` (2).
- `log_edits` (true), `allow_edit_in_battle` (false).

## Tests automatiques (GitHub Actions)
Vrai serveur Fabric avec Cobblemon 1.8.4 et LuckPerms : contenu, recettes au fer, loot, tags, physique des plaques sur de vraies entités, anti-boucle (pose et en jeu), édition complète d'un Pokémon sauvage et refus des valeurs invalides. Puis un vrai client connecté : poussée du joueur jusqu'à la Stick Plate, boucle arrêtée, orientation à la pose, pose refusée, éditeur ouvert par commande et par la baguette, captures. Résultats et jar sur la branche `ci-output`.
