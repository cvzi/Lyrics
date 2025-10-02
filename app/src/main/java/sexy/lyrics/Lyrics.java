package sexy.lyrics;

@SuppressWarnings({"unused", "WeakerAccess"})
public class Lyrics {
    private int songId;
    private String lyrics;
    private String title;
    private String artist;
    private String url;
    private boolean status;
    private String errorMessage;
    private Genius.GeniusLookUpResult[] results;

    public Lyrics() {

    }

    public Lyrics(String error) {
        status = false;
        errorMessage = error;
    }
    public int getId() {
        return songId;
    }

    public void setId(int songId) {
        this.songId = songId;
    }

    public String getLyrics() {
        return lyrics;
    }

    public void setLyrics(String lyrics) {
        this.lyrics = lyrics;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public boolean status() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Genius.GeniusLookUpResult[] getResults() {
        return results;
    }

    public void setResults(Genius.GeniusLookUpResult[] results) {
        this.results = results;
    }

}