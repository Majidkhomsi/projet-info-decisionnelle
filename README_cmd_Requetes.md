# Projet Informatique Décisionnelle - Yelp Data Warehouse

## 📌 1. Prérequis
Avant d’exécuter ce projet, assurez-vous d’avoir installé :
- **Java 8 ou supérieur** (pour exécuter Metabase)
- **Apache Spark 3.5.4** (pour l’ETL)
- **Oracle Database Client** (pour interagir avec la base Oracle)
- **PostgreSQL JDBC Driver** (inclus dans le projet)
- **SSH (pour accéder au serveur distant)**

⚠ **Important** :  
Le fichier `metabase.jar` a été supprimé pour alléger l’archive. Pour exécuter Metabase, vous devez **le télécharger à nouveau** depuis [Metabase](https://www.metabase.com/start/oss/jar.html) et le placer dans le bon dossier.

---

La commande  pour exécuter SBT avec une allocation de 16 Go de RAM, un nettoyage du projet et l’exécution de l’ETL est :

sbt -J-Xmx16G clean run
📌 Explication de la commande :
sbt → Lance Scala Build Tool.
-J-Xmx16G → Alloue 16 Go de RAM pour l'exécution de l'ETL sous Spark.
clean → Nettoie le projet (supprime les fichiers temporaires de compilation).
run → Exécute l’application principale définie dans src/main/scala.



## 🔗 2. Connexion au Serveur Oracle
1. **Se connecter en SSH** :
   ```bash
   ssh -L 1525:127.0.0.1:1521 ir618188@stendhal
Charger les variables d’environnement pour Oracle :

/opt/oraenv.sh
Vérifier la redirection de port :

ss -tln | grep ':1525'
Connexion à la base Oracle :

sqlplus ir618188/ir618188@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=127.0.0.1)(PORT=1525))(CONNECT_DATA=(SERVICE_NAME=enss2024)))
⚡ 3. Exécution de l’ETL avec Spark
L’ETL extrait, transforme et charge les données depuis PostgreSQL et JSON vers Oracle.

Aller dans le dossier du projet :

cd ~/info_decis
Exécuter l’ETL avec SBT :


sbt run
🖥️ 4. Lancement de Metabase
Télécharger Metabase (si ce n'est pas encore fait) :

wget https://downloads.metabase.com/v0.46.6/metabase.jar -O ~/metabase/metabase.jar
Lancer Metabase :

MB_JETTY_PORT=3001 MB_PLUGINS_DIR=~/metabase/plugins java -jar ~/metabase/metabase.jar

Accéder à Metabase :
Ouvrir un navigateur et aller sur http://localhost:3001
Configurer la connexion à Oracle avec :
Host : 127.0.0.1
Port : 1525
User : ir618188
Password : ir618188
Database Name : enss2024
🛠️ 5. Liste des Requêtes SQL Utilisées

📌 Top 10 des commerces les plus visités (CHECKIN)

SELECT DB.BUSINESS_ID, DB.NAME, COUNT(*) AS TOTAL_VISITS
FROM FACT_CHECKIN FC
JOIN DIM_BUSINESS DB ON FC.BUSINESS_KEY = DB.BUSINESS_KEY
GROUP BY DB.BUSINESS_ID, DB.NAME
ORDER BY TOTAL_VISITS DESC
FETCH FIRST 10 ROWS ONLY;

📌 Top 10 des utilisateurs les plus actifs

SELECT USER_KEY, COUNT(*) AS TOTAL_REVIEWS
FROM FACT_REVIEW
GROUP BY USER_KEY
ORDER BY TOTAL_REVIEWS DESC
FETCH FIRST 10 ROWS ONLY;

📌 Répartition des notes des avis


SELECT STARS, COUNT(*) AS TOTAL_REVIEWS
FROM FACT_REVIEW
GROUP BY STARS
ORDER BY STARS;

📌 Évolution des notes moyennes par mois

SELECT YEAR, MONTH, AVG(STARS) AS AVG_RATING
FROM FACT_REVIEW
JOIN DIM_DATE ON FACT_REVIEW.DATE_KEY = DIM_DATE.DATE_KEY
GROUP BY YEAR, MONTH
ORDER BY YEAR, MONTH;

📌 Les villes ayant le moins de commerces

SELECT CITY, COUNT(*) AS TOTAL_BUSINESSES
FROM DIM_BUSINESS
GROUP BY CITY
ORDER BY TOTAL_BUSINESSES ASC
FETCH FIRST 10 ROWS ONLY;

📌 Les villes où il manque des épiceries

SELECT CITY, COUNT(*) AS TOTAL_GROCERY_STORES
FROM DIM_BUSINESS
WHERE CATEGORIES LIKE '%Grocery%'
GROUP BY CITY
ORDER BY TOTAL_GROCERY_STORES ASC
FETCH FIRST 10 ROWS ONLY;
✅ 6. Résumé des étapes pour exécuter le projet
1️⃣ Connexion SSH et configuration Oracle :

ssh -L 1525:127.0.0.1:1521 ir618188@stendhal
/opt/oraenv.sh
2️⃣ Connexion SQL à Oracle :


sqlplus ir618188/ir618188@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=127.0.0.1)(PORT=1525))(CONNECT_DATA=(SERVICE_NAME=enss2024)))
3️⃣ Lancer l’ETL Spark :


cd ~/info_decis
sbt run
4️⃣ Démarrer Metabase :


MB_JETTY_PORT=3001 MB_PLUGINS_DIR=~/metabase/plugins java -jar ~/metabase/metabase.jar
5️⃣ Accéder à Metabase : http://localhost:3001

🚀 7. Conclusion
Ce projet met en place un data warehouse en modèle en étoile, utilisant Spark pour l’ETL et Metabase pour la visualisation. Il permet d’analyser les données Yelp et d’extraire des insights pertinents pour la prise de décision.
