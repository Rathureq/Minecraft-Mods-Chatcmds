# Chat Commands (Fabric, Minecraft 26.2)

Mod client : quand un joueur (toi ou un autre) écrit une commande `!x` dans le chat,
le mod répond dans le chat public.

## Commandes
| Commande | Réponse |
|---|---|
| `!paff` | `Mon paf fait X cm` (X aléatoire entre 6 et 22) |
| `!comp1` … `!comp7` | `x/1000, missing : (1000-x)//2 mN` (complétions Catacombs du floor N, profil sélectionné) |
| `!math 2+2` | `2+2 = 4` (supporte + - * / % ^ et parenthèses) |
| `!pchim` | `insta buy : X, insta sell : Y` (Chimera I, Bazaar) |
| `!precomb` | idem pour le Recombobulator 3000 |
| `!phandle` | prix BIN le plus bas du Necron's Handle à l'AH (cache 60 s) |
| `!catalvl` | `cata level : x, archer : x, mage : x, tank : x, bers : x, healer : x` |
| `!bank` | argent en banque (API banque activée requise) |
| `!sblvl` | niveau SkyBlock |
| `!inflation` | indice d'inflation (panier du Consumer Inflation Index, calculé depuis le Bazaar) |
| `!music` / `!kayoux` | messages fixes |

Anti-spam : 3 s minimum entre deux réponses à la même commande.

## Compiler
Prérequis : **JDK 25**, **Gradle 9.5+**.

    gradle wrapper --gradle-version 9.5.1
    ./gradlew build

Le jar est dans `build/libs/chatcmds-1.0.0.jar` → à mettre dans `.minecraft/mods/`
avec Fabric Loader ≥ 0.19.3 et Fabric API pour 26.2.

## Configuration (pour !comp)
Au premier lancement, `.minecraft/config/chatcmds.json` est créé :

    { "api_key": "TA_CLE_HYPIXEL", "uuid": "", "inflation_divisor": 0 }

Clé gratuite sur https://developer.hypixel.net . `uuid` vide = ton compte connecté.

`inflation_divisor` : 0 = moyenne pondérée des prix du panier. Mets le diviseur qui fait coïncider la valeur avec le graphique du site.
