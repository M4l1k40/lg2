# Contribuer à LegalFlow

## Cycle de contribution

1. Mettez `main` à jour, puis créez une branche dédiée :
   `git switch main`, `git pull`, puis `git switch -c feature/ma-fonction`,
   `fix/mon-correctif` ou `ci/mes-gardes`.
2. Travaillez et vérifiez régulièrement vos changements avec
   `git status --short`.
3. Avant chaque `git add`, relisez `git status --short` et ajoutez uniquement
   les fichiers voulus, un par un. Ne faites jamais `git add .` à l'aveugle.
4. Faites des commits courts et explicites : `feat`, `fix`, `ci`, `chore` ou
   `test` (par exemple `fix: corriger le calcul des échéances`).
5. Poussez votre branche, puis ouvrez une Pull Request vers `main`. Ne poussez
   jamais directement sur `main`.
6. Vérifiez que `ci-ok` est vert, répondez aux commentaires et attendez une
   approbation. Après validation, fusionnez la PR en **squash**.

## Avant d'ouvrir une PR

- Lancez `.\scripts\ci-local.ps1` depuis la racine du dépôt.
- Pour le frontend seul, exécutez dans `frontend` : `npm ci`, `npm run lint`,
  `npm test`, puis `npm run build`.
- Décrivez les modules touchés, les tests et les vérifications dans le modèle
  de Pull Request.

## Tests d'intégration Docker

Les PR dont la branche cible est `main` déclenchent le job d'intégration Docker.
Il démarre la stack combinée, prépare Keycloak, exécute tous les scripts
`infrastructure/scripts/test-*.ps1`, puis conserve les journaux CI pendant
7 jours. `scripts/ci-local.ps1` n'exécute pas ces tests.

En local, les scripts d'intégration utilisent les volumes Docker persistants de
LegalFlow. N'exécutez pas `docker compose down --volumes` sur une stack existante
à moins de vouloir explicitement supprimer ses données et d'avoir vérifié que
le projet Docker concerné est éphémère.

Ne commitez jamais `target/`, `node_modules/`, `dist/`, les fichiers `.env`,
des mots de passe ou tout autre secret. Si vous ajoutez accidentellement un
fichier sensible, retirez-le de l'index avant de créer le commit et prévenez
l'équipe si un secret a été exposé.
