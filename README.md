# מפות ישראל אופליין – Android

אפליקציית Android עצמאית להצגת מפות OpenStreetMap מקומיות, חיפוש בעברית ותכנון מסלול בסיסי ללא חיבור לאינטרנט.

## מה כבר ממומש בקוד
- ממשק בעברית ובכיווניות RTL.
- מנוע מפות וקטורי מקומי מבוסס Mapsforge.
- ייבוא חבילת ZIP מהמכשיר ושמירת הנתונים באחסון הפרטי של האפליקציה.
- חיפוש מקומי מתוך מסד נתונים SQLite/FTS4.
- תכנון מסלול מקומי ניסיוני על גבי גרף כבישים עבור רכב, הליכה ואופניים.
- הצגת המסלול על גבי המפה.
- אין שימוש באריחי מפה, חיפוש או שירותי ניווט מרוחקים בזמן העבודה.

## נתוני המפה
ה-workflow `.github/workflows/build-offline-data.yml` מוריד את תמצית OpenStreetMap של Israel and Palestine מ-Geofabrik, בונה מפה וקטורית בפורמט Mapsforge ומסד נתונים מקומי לחיפוש ולגרף כבישים, ומעלה ZIP כ-artifact של GitHub Actions.

החבילה מכילה:
- `israel.map` – שכבות המפה הווקטורית.
- `israel-data.db` – חיפוש מקומי וגרף כבישים.
- `README-data.txt` – פרטי מקור ורישוי.
- קובץ SHA-256 לאימות החבילה.

## התקנה
1. בונים או מורידים את APK מתוך GitHub Actions של workflow בשם **Android APK**.
2. מריצים את workflow בשם **Build offline Israel map data** ומורידים את artifact בשם `israel-offline-map-data`.
3. מעתיקים את קובץ ה-ZIP למכשיר Android.
4. פותחים את האפליקציה ובוחרים **ייבוא חבילת מפה מהמכשיר**.
5. לאחר הייבוא, המפה והחיפוש פועלים מקומית.

## מגבלות שחשוב להכיר
- מנוע המסלולים וההנחיות הקוליות הם אב-טיפוס: הוראות הפנייה נגזרות משינויי כיוון בגאומטריית המסלול, ולא משמות רחובות או מהוראות ניווט מאומתות. אין עדיין טיפול מלא בהגבלות פנייה, כבישים חסומים, נתיבים, עומסי תנועה, סטייה מהמסלול או חישוב מסלול מחדש. אין להשתמש בו כתחליף לניווט מקצועי בנהיגה.
- יש לבדוק בפועל מסלולים, חיפוש וכיסוי מפה על מכשיר לפני שימוש לנסיעה.
- נתוני המפה מבוססים על OpenStreetMap ומתעדכנים בעת הרצת workflow.
- המקור הוא OpenStreetMap contributors, דרך Geofabrik. הנתונים כפופים ל-ODbL 1.0. יש להציג ייחוס: © OpenStreetMap contributors.

## Build
GitHub Actions builds Debug and Release APKs on push or manual dispatch. The Release APK is signed with the temporary Android debug key for direct installation/testing; it is not signed with a private production key and is not ready for Play Store publication.
