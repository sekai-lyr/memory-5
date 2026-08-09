package com.sekai.song.control;

import com.sekai.song.model.DashboardStats;
import com.sekai.song.model.Playlist;
import com.sekai.song.model.SearchResult;
import com.sekai.song.model.Song;
import com.sekai.song.model.User;
import com.sekai.song.service.PlaylistService;
import com.sekai.song.service.SongService;
import com.sekai.song.util.BilibiliVideoLinkUtil;
import com.sekai.song.util.NeteaseMusicLinkUtil;
import com.sekai.song.util.QqMusicLinkUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
public class PageControl {

    @Resource
    private SongService songService;

    @Resource
    private PlaylistService playlistService;

    @GetMapping({"/", "/login"})
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/user/register")
    public String registerFallback() {
        return "redirect:/register";
    }

    @GetMapping("/user/login")
    public String loginFallback() {
        return "redirect:/login";
    }

    @GetMapping("/home")
    public String home(HttpSession session, Model model) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        List<Song> songs = songService.listLibraryByUserId(user.getId());
        List<Playlist> playlists = playlistService.listByUserId(user.getId());
        model.addAttribute("loginUser", user);
        model.addAttribute("stats", buildStats(songs, playlists));
        model.addAttribute("recentSongs", songs.stream().limit(6).toList());
        model.addAttribute("favoriteSongs", songService.listFavoritesByUserId(user.getId()).stream().limit(5).toList());
        model.addAttribute("playlists", playlists.stream().limit(4).toList());
        return "home";
    }

    @GetMapping("/songs")
    public String songs(HttpSession session,
                        Model model,
                        @RequestParam(value = "keyword", required = false) String keyword,
                        @RequestParam(value = "page", defaultValue = "1") int page) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        int pageSize = 8;
        int total = songService.countLibraryByUserId(user.getId(), keyword);
        int totalPages = Math.max((int) Math.ceil(total / (double) pageSize), 1);
        int currentPage = Math.min(Math.max(page, 1), totalPages);
        model.addAttribute("loginUser", user);
        model.addAttribute("songs", songService.listLibraryByUserId(user.getId(), keyword, currentPage, pageSize));
        model.addAttribute("playlists", playlistService.listByUserId(user.getId()));
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalCount", total);
        return "songs";
    }

    @GetMapping("/playlists")
    public String playlists(HttpSession session, Model model) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("loginUser", user);
        model.addAttribute("playlists", playlistService.listByUserId(user.getId()));
        model.addAttribute("songs", songService.listLibraryByUserId(user.getId()));
        return "playlists";
    }

    @GetMapping("/playlists/{id}")
    public String playlistDetail(@PathVariable Long id, HttpSession session, Model model) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        Playlist playlist = playlistService.getByIdAndUserId(id, user.getId());
        if (playlist == null) {
            return "redirect:/playlists";
        }
        model.addAttribute("loginUser", user);
        model.addAttribute("playlist", playlist);
        model.addAttribute("playlistSongs", playlistService.listSongs(id, user.getId()));
        model.addAttribute("songs", songService.listLibraryByUserId(user.getId()));
        return "playlist-detail";
    }

    @GetMapping("/playlists/{id}/edit")
    public String playlistEdit(@PathVariable Long id, HttpSession session, Model model) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        Playlist playlist = playlistService.getByIdAndUserId(id, user.getId());
        if (playlist == null) {
            return "redirect:/playlists";
        }
        model.addAttribute("loginUser", user);
        model.addAttribute("playlist", playlist);
        return "playlist-edit";
    }

    @GetMapping("/playlists/{id}/play")
    public String playPlaylist(@PathVariable Long id, HttpSession session) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        Playlist playlist = playlistService.getByIdAndUserId(id, user.getId());
        if (playlist == null) {
            return "redirect:/playlists";
        }
        List<Song> playlistSongs = playlistService.listSongs(id, user.getId());
        if (playlistSongs.isEmpty()) {
            return "redirect:/playlists/" + id;
        }
        return "redirect:/player/" + playlistSongs.get(0).getId() + "?playlistId=" + id + "&autoplay=1";
    }

    @GetMapping("/player/{id}")
    public String player(@PathVariable Long id,
                         @RequestParam(value = "playlistId", required = false) Long playlistId,
                         @RequestParam(value = "autoplay", required = false) String autoplay,
                         HttpSession session,
                         Model model) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        Song song = songService.getByIdAndUserId(id, user.getId());
        if (song == null) {
            return "redirect:/songs";
        }
        Playlist playlist = null;
        List<Song> queue = songService.listByUserId(user.getId());
        if (playlistId != null) {
            playlist = playlistService.getByIdAndUserId(playlistId, user.getId());
            if (playlist != null) {
                List<Song> playlistSongs = playlistService.listSongs(playlistId, user.getId());
                if (playlistSongs.stream().anyMatch(item -> Objects.equals(item.getId(), id))) {
                    queue = playlistSongs;
                }
            }
        }
        model.addAttribute("loginUser", user);
        model.addAttribute("song", song);
        model.addAttribute("playlist", playlist);
        model.addAttribute("playlistId", playlist == null ? null : playlist.getId());
        model.addAttribute("playableAudio", isPlayableAudio(song));
        model.addAttribute("isBilibiliVideo", isBilibiliVideoMusic(song));
        model.addAttribute("playableLocalVideo", isPlayableLocalVideo(song));
        model.addAttribute("startAutoplay", "1".equals(autoplay));
        model.addAttribute("lyricLines", songService.parseLyrics(song.getLyricText()));
        model.addAttribute("queue", queue);
        return "player";
    }

    @GetMapping("/favorites")
    public String favorites(HttpSession session, Model model) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("loginUser", user);
        model.addAttribute("songs", songService.listFavoritesByUserId(user.getId()));
        return "favorites";
    }

    @GetMapping("/search")
    public String search(HttpSession session, Model model, @RequestParam(value = "q", required = false) String q) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("loginUser", user);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("results", buildSearchResults(q, songService.listLibraryByUserId(user.getId()), playlistService.listByUserId(user.getId())));
        return "search";
    }

    @GetMapping("/business")
    public String business(HttpSession session, Model model) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!isBusinessAdmin(user)) {
            return "redirect:/home";
        }
        model.addAttribute("loginUser", user);
        return "business";
    }

    @GetMapping("/netease")
    public String netease(HttpSession session,
                          Model model,
                          @RequestParam(value = "link", required = false) String link) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("loginUser", user);
        model.addAttribute("link", link == null ? "" : link);
        if (!model.containsAttribute("songIds")) {
            model.addAttribute("songIds", "");
        }
        model.addAttribute("playlists", playlistService.listByUserId(user.getId()));
        model.addAttribute("neteaseLink", NeteaseMusicLinkUtil.parse(link));
        return "netease";
    }

    @GetMapping("/qq")
    public String qq(HttpSession session,
                     Model model,
                     @RequestParam(value = "link", required = false) String link) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("loginUser", user);
        model.addAttribute("link", link == null ? "" : link);
        if (!model.containsAttribute("songIds")) {
            model.addAttribute("songIds", "");
        }
        model.addAttribute("playlists", playlistService.listByUserId(user.getId()));
        model.addAttribute("qqLink", QqMusicLinkUtil.parse(link));
        return "qq";
    }

    @GetMapping("/bilibili")
    public String bilibili(HttpSession session,
                           Model model,
                           @RequestParam(value = "link", required = false) String link) {
        User user = requireUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("loginUser", user);
        model.addAttribute("link", link == null ? "" : link);
        if (!model.containsAttribute("videoIds")) {
            model.addAttribute("videoIds", "");
        }
        model.addAttribute("playlists", playlistService.listByUserId(user.getId()));
        model.addAttribute("bilibiliLink", BilibiliVideoLinkUtil.parse(link));
        return "bilibili";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    private DashboardStats buildStats(List<Song> songs, List<Playlist> playlists) {
        DashboardStats stats = new DashboardStats();
        stats.setSongCount(songs.size());
        stats.setPlaylistCount(playlists.size());
        stats.setFavoriteCount((int) songs.stream().filter(song -> Boolean.TRUE.equals(song.getFavorite())).count());
        stats.setTopAnime(findTop(songs.stream().map(Song::getAnimeTitle).toList()));
        stats.setTopMood(findTop(songs.stream().map(Song::getMood).toList()));
        return stats;
    }

    private List<SearchResult> buildSearchResults(String q, List<Song> songs, List<Playlist> playlists) {
        String keyword = q == null ? "" : q.trim().toLowerCase();
        if (keyword.isEmpty()) {
            return List.of();
        }
        List<SearchResult> results = new ArrayList<>();
        songs.stream()
                .filter(song -> contains(keyword, song.getTitle(), song.getArtist(), song.getAnimeTitle(), song.getTags(), song.getMood()))
                .forEach(song -> results.add(new SearchResult("歌曲", song.getTitle(),
                        compact(song.getArtist(), song.getAnimeTitle(), song.getMood()), "/player/" + song.getId())));
        playlists.stream()
                .filter(playlist -> contains(keyword, playlist.getName(), playlist.getDescription()))
                .forEach(playlist -> results.add(new SearchResult("歌单", playlist.getName(),
                        playlist.getDescription(), "/playlists/" + playlist.getId())));
        return results;
    }

    private String findTop(List<String> values) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(value -> counts.merge(value, 1, Integer::sum));
        return counts.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("暂无");
    }

    private boolean contains(String keyword, String... values) {
        for (String value : values) {
            if (value != null && value.toLowerCase().contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String compact(String... values) {
        return String.join(" · ", java.util.Arrays.stream(values)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .toList());
    }

    private boolean isPlayableAudio(Song song) {
        return song != null && song.getLocalAudioUrl() != null && !song.getLocalAudioUrl().isBlank();
    }

    private boolean isPlayableLocalVideo(Song song) {
        return isBilibiliVideoMusic(song)
                && song.getLocalVideoUrl() != null
                && !song.getLocalVideoUrl().isBlank();
    }

    private boolean isBilibiliVideoMusic(Song song) {
        return song != null
                && song.getAudioUrl() != null
                && song.getAudioUrl().contains("bilibili.com");
    }

    private User requireUser(HttpSession session) {
        Object loginUser = session.getAttribute("loginUser");
        if (loginUser instanceof User user) {
            return user;
        }
        return null;
    }

    private boolean isBusinessAdmin(User user) {
        return user != null && "sekai".equalsIgnoreCase(user.getUserName());
    }
}
