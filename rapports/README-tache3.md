# IFT3913 — Tâche 3 : Semgrep et GitHub Actions

## Membres du binôme

- **Prénom Nom** — Matricule : `XXXXXXXX`
- **Prénom Nom** — Matricule : `XXXXXXXX`

## 1. Dépôt et fichiers principaux

- Dépôt GitHub : https://github.com/ReddKingK17/tika
- Workflow : [`.github/workflows/tache3-ci.yml`](../.github/workflows/tache3-ci.yml)
- Action Rickroll : [`.github/actions/rickroll/action.yml`](../.github/actions/rickroll/action.yml)
- Rapport avant les modifications : [`baseline.json`](baseline.json)
- Rapport après les modifications : [`after.json`](after.json)
- Pull request des vulnérabilités : https://github.com/ReddKingK17/tika/pull/2

## 2. Modification du workflow GitHub Actions

Le workflow ajouté pour la tâche 3 se trouve dans `.github/workflows/tache3-ci.yml`. Il s’exécute lors :

- d’un push sur la branche `main`;
- d’une pull request vers `main`;
- d’un lancement manuel avec `workflow_dispatch`.

Il contient deux jobs :

1. l’exécution des tests du cas d’étude Apache Tika;
2. la détection des nouvelles vulnérabilités avec Semgrep.

### 2.1 Tests exécutés

Le workflow exécute les tests du module `tika-serialization` avec la commande suivante :

```bash
./mvnw \
  -pl tika-serialization \
  -am \
  test \
  -Dtest=ComponentNameResolverChatUniTestTest,ComponentRegistryMutationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Djacoco.skip=true
```

L’option `-pl tika-serialization` sélectionne le module étudié, tandis que `-am` construit également les modules dont il dépend.

## 3. Configuration de Semgrep

Semgrep est exécuté avec les trois configurations suivantes :

- `p/java`;
- `p/security-audit`;
- `p/owasp-top-ten`.

La même configuration a été utilisée pour produire le rapport initial et le rapport final. Cela permet de comparer les deux analyses dans des conditions identiques.

### 3.1 Récupération de l’historique Git

Le workflow récupère l’historique Git complet avec :

```yaml
with:
  fetch-depth: 0
```

Cet historique est nécessaire pour que Semgrep puisse comparer le code courant à un commit de référence.

### 3.2 Détermination du commit de référence

Le workflow détermine le commit de comparaison selon l’événement :

- pour une pull request, il utilise le SHA de la branche cible;
- pour un push, il utilise le SHA précédant le push;
- pour une exécution manuelle, il utilise le parent du commit courant.

Le SHA obtenu est enregistré dans la variable d’environnement `BASE_SHA`.

### 3.3 Détection des nouvelles vulnérabilités

La commande principale est :

```bash
semgrep scan \
  --config p/java \
  --config p/security-audit \
  --config p/owasp-top-ten \
  --baseline-commit "$BASE_SHA" \
  --error \
  --json \
  --output reports/semgrep-new-findings.json \
  .
```

L’option `--baseline-commit` limite les résultats bloquants au code introduit depuis le commit de référence. Les vulnérabilités déjà présentes dans le projet ne sont donc pas considérées comme nouvellement introduites.

L’option `--error` produit un code de sortie différent de zéro lorsque Semgrep détecte un nouveau résultat bloquant. Cela provoque l’échec du job GitHub Actions, conformément à l’objectif de la tâche.

### 3.4 Conservation du rapport

Le rapport JSON produit par Semgrep est téléversé comme artefact avec `actions/upload-artifact`.

La condition suivante est utilisée :

```yaml
if: always()
```

Elle permet de conserver le rapport même lorsque Semgrep fait volontairement échouer le job.

### 3.5 Sécurisation des actions utilisées

Les actions GitHub externes sont référencées par leur SHA complet plutôt que seulement par un tag. Cette décision empêche qu’une modification ultérieure d’un tag change silencieusement le code exécuté par le workflow.

## 4. Action réutilisable Rickroll

L’élément humoristique demandé est implémenté comme une action composite locale et réutilisable :

```text
.github/actions/rickroll/action.yml
```

Elle est invoquée lorsque les tests du cas d’étude échouent :

```yaml
- name: Rickroll when tests fail
  if: failure()
  uses: ./.github/actions/rickroll
```

L’action affiche une annotation GitHub contenant le message :

> Never gonna give you up, never gonna let you down!

Elle ajoute également le lien vers la vidéo de Rick Astley :

```text
https://www.youtube.com/watch?v=dQw4w9WgXcQ
```

Le choix d’une action composite locale permet de séparer le Rickroll du workflow principal et de le réutiliser dans d’autres jobs.

## 5. Validation du workflow

### 5.1 Validation sans nouvelle vulnérabilité

Avant l’introduction des sept vulnérabilités, le workflow a terminé avec succès. Cela confirme que les résultats déjà présents dans Apache Tika ne provoquent pas incorrectement l’échec du nouveau workflow.

Exécution réussie :

https://github.com/ReddKingK17/tika/actions/runs/41355778779

### 5.2 Validation du Rickroll

Un test volontairement défaillant a été ajouté temporairement afin de vérifier le comportement de l’action réutilisable.

Le job de tests a échoué et l’étape `Rickroll when tests fail` a été exécutée. L’annotation suivante est visible dans l’exécution :

> Never gonna give you up, never gonna let you down!

Après cette validation, le changement temporaire a été retiré et les tests ont de nouveau réussi.

Preuve de validation :

https://github.com/ReddKingK17/tika/actions/runs/41354881544

### 5.3 Validation avec les vulnérabilités

Après l’introduction des sept vulnérabilités, le job de tests a réussi, mais le job Semgrep a échoué avec le code de sortie `1`.

Ce résultat est attendu : il démontre que le workflow détecte et bloque du code qui introduit de nouvelles vulnérabilités.

Preuves finales :

- Exécution sur `main` : https://github.com/ReddKingK17/tika/actions/runs/43318154267
- Job Semgrep : https://github.com/ReddKingK17/tika/actions/runs/43318154267/job/131531480201
- Pull request : https://github.com/ReddKingK17/tika/pull/2

Le dernier run montre :

| Job | Résultat |
|---|---|
| `Task 3 tests` | Réussi |
| `Semgrep new vulnerabilities` | Échec attendu |
| Artefact `semgrep-new-findings` | Produit |

Les builds Java avaient initialement échoué à cause de l’ordre des imports dans `TikaClient.java`, détecté par Spotless. L’ordre des imports a été corrigé avant la fusion finale.

## 6. Comparaison des rapports Semgrep

Les deux rapports ont été produits avec Semgrep et la même configuration.

| Analyse | Nombre de résultats |
|---|---:|
| Avant les modifications | 42 |
| Après les modifications | 51 |
| Nouveaux résultats | 9 |

Les neuf nouveaux résultats correspondent aux sept vulnérabilités introduites.

La vulnérabilité du gestionnaire de certificats TLS produit trois résultats, car Semgrep signale séparément ses trois méthodes de validation vides. Les six autres vulnérabilités produisent chacune un résultat.

Les sept vulnérabilités correspondent à sept règles Semgrep distinctes. DES et le contexte SSL faible partagent le CWE-326, mais constituent deux types techniques différents et sont détectés par deux règles différentes.

## 7. Résumé des vulnérabilités introduites

| Nº | Type de vulnérabilité | CWE | Fichier modifié | Résultats |
|---:|---|---|---|---:|
| 1 | Désérialisation non sûre | CWE-502 | `ServiceLoader.java` | 1 |
| 2 | Hachage faible MD5 | CWE-328 | `InputStreamDigester.java` | 1 |
| 3 | Chiffrement DES obsolète | CWE-326 | `EncryptedPrescriptionParser.java` | 1 |
| 4 | Générateur aléatoire non sécurisé | CWE-330 | `TikaClient.java` | 1 |
| 5 | Validation de certificat TLS désactivée | CWE-295 | `TikaClient.java` | 3 |
| 6 | Contexte SSL faible | CWE-326 | `HttpClientFactory.java` | 1 |
| 7 | Injection SQL | CWE-89 | `DBBuffer.java` | 1 |

## 8. Vulnérabilité 1 : désérialisation non sûre

- **Type :** désérialisation de données non fiables
- **CWE :** CWE-502
- **Commit :** `2316dec68`
- **Ligne signalée :** 398

### 8.1 Fichier modifié

```text
tika-core/src/main/java/org/apache/tika/config/ServiceLoader.java
```

Cette classe charge des composants et des services. Une méthode restaurant un composant précédemment enregistré paraît donc cohérente avec son rôle.

### 8.2 Code introduit

```java
public static Object loadSavedComponent(InputStream in)
        throws IOException, ClassNotFoundException {
    return new ObjectInputStream(in).readObject();
}
```

### 8.3 Pourquoi le code est vulnérable

La méthode désérialise directement un flux fourni par l’appelant, sans vérifier son origine ni les classes qu’il contient.

Pendant `readObject()`, Java peut reconstruire des objets contrôlés par un attaquant. En présence de classes exploitables dans le classpath, un flux malveillant peut provoquer des opérations inattendues et potentiellement une exécution de code arbitraire.

Un correctif serait d’éviter la sérialisation Java native pour les données externes ou d’utiliser `ObjectInputFilter` afin de limiter explicitement les classes autorisées.

### 8.4 Résultat Semgrep

- Règle : `java.lang.security.audit.object-deserialization.object-deserialization`
- Résultats nouvellement introduits : **1**
- Absent du rapport initial.

## 9. Vulnérabilité 2 : hachage faible MD5

- **Type :** algorithme de hachage faible
- **CWE :** CWE-328
- **Commit :** `34a8e3201`
- **Ligne signalée :** 126

### 9.1 Fichier modifié

```text
tika-core/src/main/java/org/apache/tika/digest/InputStreamDigester.java
```

Cette classe calcule déjà des empreintes numériques de flux. Une méthode de somme de contrôle rapide est donc plausible dans ce fichier.

### 9.2 Code introduit

```java
public static byte[] quickChecksum(byte[] data)
        throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("MD5");
    return md.digest(data);
}
```

### 9.3 Pourquoi le code est vulnérable

MD5 n’est plus résistant aux collisions. Deux contenus différents peuvent être construits afin de produire la même empreinte.

Si cette empreinte sert à vérifier l’intégrité ou l’authenticité d’un fichier, un attaquant pourrait remplacer le fichier sans que la vérification détecte nécessairement la modification.

Un correctif serait d’utiliser SHA-256, SHA-512 ou un HMAC lorsqu’une authentification est nécessaire.

### 9.4 Résultat Semgrep

- Règle : `java.lang.security.audit.crypto.use-of-md5.use-of-md5`
- Résultats nouvellement introduits : **1**
- Absent du rapport initial.

## 10. Vulnérabilité 3 : chiffrement DES obsolète

- **Type :** algorithme de chiffrement obsolète
- **CWE :** CWE-326
- **Commit :** `776074a46`
- **Ligne signalée :** 64

### 10.1 Fichier modifié

```text
tika-example/src/main/java/org/apache/tika/example/EncryptedPrescriptionParser.java
```

Cette classe manipule déjà des opérations cryptographiques et des ordonnances chiffrées. Une méthode de chiffrement paraît donc cohérente avec sa fonction.

### 10.2 Code introduit

```java
public static byte[] legacyEncrypt(byte[] data, Key key)
        throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance("DES");
    cipher.init(Cipher.ENCRYPT_MODE, key);
    return cipher.doFinal(data);
}
```

### 10.3 Pourquoi le code est vulnérable

DES utilise une clé effective de seulement 56 bits et n’offre plus une protection suffisante contre les attaques par force brute.

De plus, demander simplement l’algorithme `"DES"` peut sélectionner le mode ECB, qui révèle les motifs répétitifs du texte clair.

Un correctif serait d’utiliser un chiffrement authentifié moderne, comme `AES/GCM/NoPadding`.

### 10.4 Résultat Semgrep

- Règle : `java.lang.security.audit.crypto.des-is-deprecated.des-is-deprecated`
- Résultats nouvellement introduits : **1**
- Absent du rapport initial.

## 11. Vulnérabilité 4 : générateur aléatoire non sécurisé

- **Type :** génération prévisible d’un jeton
- **CWE :** CWE-330
- **Commit :** `8bdbcb93d`
- **Ligne signalée :** 76

### 11.1 Fichier modifié

```text
tika-server/tika-server-client/src/main/java/org/apache/tika/server/client/TikaClient.java
```

Cette classe représente un client HTTP de Tika. Une méthode générant un jeton pour identifier les requêtes est donc plausible dans ce fichier.

### 11.2 Code introduit

```java
public static String newSessionToken() {
    return Long.toHexString(new Random().nextLong());
}
```

### 11.3 Pourquoi le code est vulnérable

`java.util.Random` n’est pas un générateur aléatoire cryptographiquement sécurisé. Son état interne peut être prédit à partir de certaines sorties observées.

Un attaquant pourrait donc tenter de prédire de futurs jetons et usurper une session ou une requête.

Un correctif serait d’utiliser `java.security.SecureRandom` et des jetons possédant suffisamment d’entropie.

### 11.4 Résultat Semgrep

- Règle : `java.lang.security.audit.crypto.weak-random.weak-random`
- Résultats nouvellement introduits : **1**
- Absent du rapport initial.

## 12. Vulnérabilité 5 : validation de certificat TLS désactivée

- **Type :** validation incorrecte de certificat
- **CWE :** CWE-295
- **Commit :** `46bd605ba`
- **Lignes signalées :** 83, 87 et 91

### 12.1 Fichier modifié

```text
tika-server/tika-server-client/src/main/java/org/apache/tika/server/client/TikaClient.java
```

Comme `TikaClient` établit des communications avec un serveur, un gestionnaire de certificats TLS paraît plausible dans cette classe.

### 12.2 Code introduit

```java
public static class LenientTrustManager implements X509TrustManager {
    @Override
    public void checkClientTrusted(
            X509Certificate[] chain, String authType) {
    }

    @Override
    public void checkServerTrusted(
            X509Certificate[] chain, String authType) {
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }
}
```

### 12.3 Pourquoi le code est vulnérable

Les méthodes de validation sont vides. Le gestionnaire accepte donc des certificats sans vérifier leur chaîne de confiance.

Un attaquant placé sur le réseau pourrait présenter un faux certificat, intercepter la connexion et lire ou modifier les communications.

Un correctif serait d’utiliser le gestionnaire de confiance par défaut de la JVM ou un `TrustManagerFactory` configuré avec un magasin de certificats fiable.

### 12.4 Résultat Semgrep

- Règle : `java.lang.security.audit.crypto.ssl.insecure-trust-manager.insecure-trust-manager`
- Résultats nouvellement introduits : **3**
- Un résultat est produit pour chacune des trois méthodes.
- Absent du rapport initial.

## 13. Vulnérabilité 6 : contexte SSL faible

- **Type :** utilisation d’un protocole cryptographique faible
- **CWE :** CWE-326
- **Commit :** `18a1faea9`
- **Ligne signalée :** 504

### 13.1 Fichier modifié

```text
tika-pipes/tika-httpclient-commons/src/main/java/org/apache/tika/client/HttpClientFactory.java
```

Cette classe construit les clients HTTP de Tika et utilise déjà des objets liés aux connexions TLS. Une méthode créant un contexte SSL paraît donc cohérente avec son rôle.

### 13.2 Code introduit

```java
public static SSLContext legacySslContext()
        throws NoSuchAlgorithmException {
    return SSLContext.getInstance("SSL");
}
```

### 13.3 Pourquoi le code est vulnérable

Les versions de SSL sont obsolètes et vulnérables à plusieurs attaques connues. L’utilisation du nom générique `"SSL"` peut permettre l’emploi d’un protocole ancien ou une rétrogradation vers une version faible.

Même si certains JDK récents désactivent ces protocoles par défaut, demander un contexte SSL obsolète reste une mauvaise pratique.

Un correctif serait d’utiliser TLS 1.2 ou TLS 1.3.

### 13.4 Résultat Semgrep

- Règle : `java.lang.security.audit.weak-ssl-context.weak-ssl-context`
- Résultats nouvellement introduits : **1**
- Absent du rapport initial.

## 14. Vulnérabilité 7 : injection SQL

- **Type :** construction non sécurisée d’une requête SQL
- **CWE :** CWE-89
- **Commit :** `89de743bd`
- **Ligne signalée :** 60

### 14.1 Fichier modifié

```text
tika-eval/tika-eval-app/src/main/java/org/apache/tika/eval/app/db/DBBuffer.java
```

Cette classe utilise déjà JDBC et travaille avec une base de données. Une méthode recherchant des lignes par leur nom est donc plausible dans ce fichier.

### 14.2 Code introduit

```java
public static ResultSet findByName(Connection conn, String name)
        throws SQLException {
    Statement st = conn.createStatement();
    return st.executeQuery(
            "SELECT * FROM buffer WHERE name = '" + name + "'");
}
```

### 14.3 Pourquoi le code est vulnérable

Le paramètre `name` est concaténé directement dans la requête SQL.

Une valeur comme :

```text
' OR '1'='1
```

peut modifier la structure de la requête et permettre de retourner toutes les lignes. Une entrée plus élaborée pourrait également permettre de lire ou modifier d’autres données.

Le correctif serait d’utiliser une requête préparée :

```java
PreparedStatement statement =
        conn.prepareStatement(
                "SELECT * FROM buffer WHERE name = ?");
statement.setString(1, name);
```

### 14.4 Résultat Semgrep

- Règle : `java.lang.security.audit.formatted-sql-string.formatted-sql-string`
- Résultats nouvellement introduits : **1**
- Aucun résultat de cette règle n’était présent dans `DBBuffer.java` avant la modification.

## 15. Conclusion

Le workflow GitHub Actions remplit le comportement attendu :

- les tests sélectionnés du cas d’étude Apache Tika sont exécutés automatiquement;
- une action réutilisable Rickroll est invoquée lorsqu’un test échoue;
- le code courant est comparé à un commit de référence;
- les anciennes alertes Semgrep ne bloquent pas seules un nouveau commit;
- une nouvelle vulnérabilité détectée fait échouer le job;
- le rapport JSON est conservé comme artefact;
- les rapports avant et après permettent de vérifier la différence.

L’analyse initiale contient 42 résultats. Après l’introduction des sept vulnérabilités, l’analyse contient 51 résultats. Les neuf résultats supplémentaires sont expliqués par les sept vulnérabilités ajoutées.

Le run de validation avec les vulnérabilités confirme le comportement recherché : les tests réussissent, tandis que Semgrep échoue volontairement en raison des nouveaux résultats.
