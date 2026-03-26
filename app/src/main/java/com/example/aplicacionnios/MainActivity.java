package com.example.aplicacionnios;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private int gridRows;
    private int gridCols;
    private GridLayout gridLayout;
    private TextView tvWordsToFind;
    private TextView tvStreak;
    private LineDrawingView lineDrawingView;
    private TextView[][] cellViews;
    private char[][] grid;

    private final List<String> allWords = new ArrayList<>();
    private final List<String> currentWords = new ArrayList<>();
    private final Set<String> wordsFound = new HashSet<>();
    private Map<String, List<int[]>> wordPaths = new HashMap<>(); // Track actual paths for each word
    private int streakCount = 0;

    private final List<TextView> selectedCells = new ArrayList<>();
    private final StringBuilder currentSelection = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Strandy);
        
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        gridLayout = findViewById(R.id.gridLayout);
        tvWordsToFind = findViewById(R.id.tvWordsToFind);
        tvStreak = findViewById(R.id.tvStreak);
        lineDrawingView = findViewById(R.id.lineDrawingView);
        Button btnLoadCsv = findViewById(R.id.btnLoadCsv);

        loadWordsFromAsset();

        btnLoadCsv.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("text/*");
            csvPickerLauncher.launch(intent);
        });

        startNewGame();
    }

    private final ActivityResultLauncher<Intent> csvPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    loadWordsFromUri(uri);
                    streakCount = 0; // Reset streak when loading new CSV
                    tvStreak.setText(getString(R.string.streak_format, 0));
                    startNewGame();
                }
            }
    );

    private void loadWordsFromAsset() {
        allWords.clear();
        try (InputStream is = getAssets().open("animales.csv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim().toUpperCase();
                if (word.length() >= 3 && word.length() <= 12 && word.matches("\\p{L}+")) {
                    allWords.add(word);
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error loading asset", e);
        }
    }

    private void loadWordsFromUri(Uri uri) {
        allWords.clear();
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim().toUpperCase();
                if (word.length() >= 3 && word.length() <= 12 && word.matches("\\p{L}+")) {
                    allWords.add(word);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.failed_load_csv, Toast.LENGTH_SHORT).show();
        }
    }

    private void startNewGame() {
        if (allWords.isEmpty()) {
            Toast.makeText(this, R.string.no_valid_words, Toast.LENGTH_LONG).show();
            return;
        }

        currentWords.clear();
        wordsFound.clear();
        wordPaths.clear();
        Random random = new Random();
        
        boolean foundValidSet = false;
        
        for (int attempts = 0; attempts < 200 && !foundValidSet; attempts++) {
            currentWords.clear();
            
            int targetWordCount = 3 + random.nextInt(2);
            
            Set<Character> usedChars = new HashSet<>();
            boolean allowRepeats = (attempts > 100); // After 100 attempts, allow repeats
            
            int wordAttempts = 0;
            while (currentWords.size() < targetWordCount && wordAttempts < allWords.size() * 2) {
                wordAttempts++;
                String candidate = allWords.get(random.nextInt(allWords.size()));
                
                if (currentWords.contains(candidate)) continue;
                
                if (candidate.length() < 3 || candidate.length() > 6) {
                    if (wordAttempts < allWords.size()) continue; // Try to avoid very short/long words initially
                }
                
                boolean hasConflict = false;
                if (!allowRepeats) {
                    for (char c : candidate.toCharArray()) {
                        if (usedChars.contains(c)) {
                            hasConflict = true;
                            break;
                        }
                    }
                }
                
                if (!hasConflict) {
                    currentWords.add(candidate);
                    for (char c : candidate.toCharArray()) {
                        usedChars.add(c);
                    }
                }
            }
            
            if (currentWords.size() < 3) continue;
            
            int totalLetters = 0;
            for (String w : currentWords) {
                totalLetters += w.length();
            }
            if (totalLetters > 30) continue; // Too many letters for 6x6 grid
            
            for (int shuffle = 0; shuffle < 20; shuffle++) {
                if (tryGenerateBoardSimple(random)) {
                    foundValidSet = true;
                    break;
                }
            }
        }
        
        if (!foundValidSet) {
            currentWords.clear();
            wordPaths.clear();
            
            Random reRandom = new Random();
            int targetCount = 3;
            
            while (currentWords.size() < targetCount && currentWords.size() < allWords.size()) {
                String candidate = allWords.get(reRandom.nextInt(allWords.size()));
                if (!currentWords.contains(candidate) && candidate.length() >= 3 && candidate.length() <= 5) {
                    currentWords.add(candidate);
                }
            }
            
            for (int lastTry = 0; lastTry < 30; lastTry++) {
                if (tryGenerateBoardSimple(reRandom)) {
                    foundValidSet = true;
                    Toast.makeText(this, R.string.board_relaxed, Toast.LENGTH_SHORT).show();
                    break;
                }
            }
            
            if (!foundValidSet) {
                Toast.makeText(this, R.string.failed_gen_board, Toast.LENGTH_LONG).show();
                return; // Don't proceed if we can't generate
            }
        }

        updateWordsToFindText();
        setupGrid();
        displayBoard();
    }

    private boolean tryGenerateBoardSimple(Random random) {
        int workingSize = 12; // Reduced from 25 since max board is 6x6
        char[][] workingGrid = new char[workingSize][workingSize];
        
        for (int r = 0; r < workingSize; r++) {
            for (int c = 0; c < workingSize; c++) {
                workingGrid[r][c] = '\0';
            }
        }
        
        wordPaths.clear();
        
        int startRow = workingSize / 2;
        int startCol = workingSize / 2;
        
        for (int wordIndex = 0; wordIndex < currentWords.size(); wordIndex++) {
            String word = currentWords.get(wordIndex);
            boolean placed = false;
            int attempts = 0;
            
            while (!placed && attempts < 1000) {
                attempts++;
                
                int tryRow, tryCol;
                
                if (wordIndex == 0) {
                    tryRow = startRow + random.nextInt(6) - 3;
                    tryCol = startCol + random.nextInt(6) - 3;
                } else {
                    List<int[]> adjacentCells = new ArrayList<>();
                    for (int r = 1; r < workingSize - 1; r++) {
                        for (int c = 1; c < workingSize - 1; c++) {
                            if (workingGrid[r][c] == '\0') {
                                boolean hasNeighbor = false;
                                for (int dr = -1; dr <= 1; dr++) {
                                    for (int dc = -1; dc <= 1; dc++) {
                                        if (dr == 0 && dc == 0) continue;
                                        if (workingGrid[r + dr][c + dc] != '\0') {
                                            hasNeighbor = true;
                                            break;
                                        }
                                    }
                                    if (hasNeighbor) break;
                                }
                                if (hasNeighbor) {
                                    adjacentCells.add(new int[]{r, c});
                                }
                            }
                        }
                    }
                    
                    if (adjacentCells.isEmpty()) {
                        tryRow = startRow + random.nextInt(Math.min(10, workingSize - startRow)) - 5;
                        tryCol = startCol + random.nextInt(Math.min(10, workingSize - startCol)) - 5;
                    } else {
                        int[] chosen = adjacentCells.get(random.nextInt(adjacentCells.size()));
                        tryRow = chosen[0];
                        tryCol = chosen[1];
                    }
                }
                
                if (tryRow >= 2 && tryRow < workingSize - 2 && 
                    tryCol >= 2 && tryCol < workingSize - 2) {
                    
                    List<int[]> path = new ArrayList<>();
                    if (placeWordAsPath(workingGrid, word, tryRow, tryCol, 0, path, random, workingSize)) {
                        if (path.size() != word.length()) {
                            continue;
                        }
                        for (int i = 0; i < word.length(); i++) {
                            int[] pos = path.get(i);
                            workingGrid[pos[0]][pos[1]] = word.charAt(i);
                        }
                        wordPaths.put(word, new ArrayList<>(path));
                        placed = true;
                    }
                }
            }
            
            if (!placed) {
                return false; // Couldn't place this word
            }
        }
        
        int minRow = workingSize, maxRow = -1;
        int minCol = workingSize, maxCol = -1;
        
        for (int r = 0; r < workingSize; r++) {
            for (int c = 0; c < workingSize; c++) {
                if (workingGrid[r][c] != '\0') {
                    minRow = Math.min(minRow, r);
                    maxRow = Math.max(maxRow, r);
                    minCol = Math.min(minCol, c);
                    maxCol = Math.max(maxCol, c);
                }
            }
        }
        
        gridRows = maxRow - minRow + 1;
        gridCols = maxCol - minCol + 1;
        
        if (gridRows < 3 || gridCols < 3 || gridRows > 6 || gridCols > 6) {
            return false; // Size not suitable
        }
        
        grid = new char[gridRows][gridCols];
        cellViews = new TextView[gridRows][gridCols];
        
        for (int r = 0; r < gridRows; r++) {
            System.arraycopy(workingGrid[r + minRow], minCol, grid[r], 0, gridCols);
        }
        
        Map<String, List<int[]>> adjustedPaths = new HashMap<>();
        for (Map.Entry<String, List<int[]>> entry : wordPaths.entrySet()) {
            List<int[]> adjustedPath = new ArrayList<>();
            for (int[] pos : entry.getValue()) {
                adjustedPath.add(new int[]{pos[0] - minRow, pos[1] - minCol});
            }
            adjustedPaths.put(entry.getKey(), adjustedPath);
        }
        wordPaths = adjustedPaths;
        
        for (String word : currentWords) {
            List<int[]> path = wordPaths.get(word);
            if (path == null || path.size() != word.length()) {
                return false;
            }
            for (int i = 0; i < word.length(); i++) {
                int[] pos = path.get(i);
                if (grid[pos[0]][pos[1]] != word.charAt(i)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private boolean placeWordAsPath(char[][] workingGrid, String word, int row, int col, 
                                    int charIndex, List<int[]> path, Random random, int gridSize) {
        if (row < 0 || row >= gridSize || col < 0 || col >= gridSize) {
            return false;
        }
        
        if (workingGrid[row][col] != '\0') {
            return false;
        }
        
        for (int[] pos : path) {
            if (pos[0] == row && pos[1] == col) {
                return false;
            }
        }
        
        path.add(new int[]{row, col});
        
        if (charIndex == word.length() - 1) {
            return true;
        }
        
        int[][] directions = {
            {-1, 0}, {-1, 1}, {0, 1}, {1, 1},
            {1, 0}, {1, -1}, {0, -1}, {-1, -1}
        };
        
        List<int[]> dirList = new ArrayList<>();
        Collections.addAll(dirList, directions);
        Collections.shuffle(dirList, random);
        
        for (int[] dir : dirList) {
            int newRow = row + dir[0];
            int newCol = col + dir[1];
            
            if (placeWordAsPath(workingGrid, word, newRow, newCol, charIndex + 1, path, random, gridSize)) {
                return true;
            }
        }
        
        path.remove(path.size() - 1);
        return false;
    }
    
    private void displayBoard() {
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                if (grid[r][c] == '\0') {
                    cellViews[r][c].setText("⟡");
                    cellViews[r][c].setBackgroundResource(R.drawable.circle_cell);
                    cellViews[r][c].setTextColor(Color.WHITE);
                    cellViews[r][c].setVisibility(android.view.View.VISIBLE);
                } else {
                    cellViews[r][c].setText(String.valueOf(grid[r][c]));
                    cellViews[r][c].setBackgroundResource(R.drawable.circle_cell);
                    cellViews[r][c].setTextColor(Color.parseColor("#004D40"));
                    cellViews[r][c].setVisibility(android.view.View.VISIBLE);
                }
            }
        }
    }

    private void updateWordsToFindText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < currentWords.size(); i++) {
            String word = currentWords.get(i);
            if (wordsFound.contains(word)) {
                sb.append("<strike>").append(word).append("</strike>");
            } else {
                sb.append(word);
            }
            if (i < currentWords.size() - 1) {
                sb.append(", ");
            }
        }
        tvWordsToFind.setText(android.text.Html.fromHtml(sb.toString(), android.text.Html.FROM_HTML_MODE_LEGACY));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupGrid() {
        gridLayout.removeAllViews();
        gridLayout.setColumnCount(gridCols);
        gridLayout.setRowCount(gridRows);

        int cellSize = getCellSize();

        int textSize = (int) (cellSize * 0.35);
        
        int margin = 3;
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                TextView tv = new TextView(this);
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize);
                tv.setGravity(Gravity.CENTER);
                tv.setBackgroundResource(R.drawable.circle_cell);
                tv.setTextColor(Color.parseColor("#004D40")); // Dark Teal

                GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                        GridLayout.spec(r, 1f),
                        GridLayout.spec(c, 1f)
                );
                params.width = cellSize;
                params.height = cellSize;
                params.setMargins(margin, margin, margin, margin);
                tv.setLayoutParams(params);

                tv.setTag(r * gridCols + c); // Unique ID based on index
                cellViews[r][c] = tv;
                gridLayout.addView(tv);
            }
        }

        gridLayout.setOnTouchListener((v, event) -> {
            float x = event.getX();
            float y = event.getY();
            int action = event.getAction();

            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                handleTouchPosition(x, y);
            } else if (action == MotionEvent.ACTION_UP) {
                v.performClick();
                checkSelection();
            }
            return true;
        });
    }

    private int getCellSize() {
        android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        int availableWidth = (int) (screenWidth * 0.90);
        int availableHeight = (int) (screenHeight * 0.65); // Leave space for title and buttons

        int cellSizeByWidth = availableWidth / gridCols;
        int cellSizeByHeight = availableHeight / gridRows;

        int cellSize = Math.min(cellSizeByWidth, cellSizeByHeight);
        return cellSize;
    }

    private void handleTouchPosition(float x, float y) {
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                if (grid[r][c] == '\0') continue;
                
                TextView tv = cellViews[r][c];
                if (isPointInsideView(x, y, tv)) {
                    if (!selectedCells.contains(tv) && tv.getCurrentTextColor() != Color.WHITE) {
                        tv.setBackgroundResource(R.drawable.circle_cell_selected);
                        
                        if (!selectedCells.isEmpty()) {
                            TextView prevCell = selectedCells.get(selectedCells.size() - 1);
                            float prevX = prevCell.getLeft() + prevCell.getWidth() / 2f;
                            float prevY = prevCell.getTop() + prevCell.getHeight() / 2f;
                            float currX = tv.getLeft() + tv.getWidth() / 2f;
                            float currY = tv.getTop() + tv.getHeight() / 2f;
                            lineDrawingView.addLine(prevX, prevY, currX, currY);
                        }
                        
                        selectedCells.add(tv);
                        currentSelection.append(tv.getText().toString());
                    }
                }
            }
        }
    }

    private boolean isPointInsideView(float x, float y, View view) {
        float relativeX = view.getLeft();
        float relativeY = view.getTop();
        
        return (x >= relativeX && x <= relativeX + view.getWidth() &&
                y >= relativeY && y <= relativeY + view.getHeight());
    }

    private void checkSelection() {
        String word = currentSelection.toString();
        
        if (currentWords.contains(word) && !wordsFound.contains(word)) {
            List<int[]> correctPath = wordPaths.get(word);
            
            boolean pathMatches = false;
            if (correctPath != null && correctPath.size() == selectedCells.size()) {
                pathMatches = true;
                for (int i = 0; i < correctPath.size(); i++) {
                    int[] pathCell = correctPath.get(i);
                    TextView selectedCell = selectedCells.get(i);
                    int selectedTag = (int) selectedCell.getTag();
                    int pathTag = pathCell[0] * gridCols + pathCell[1];
                    
                    if (selectedTag != pathTag) {
                        pathMatches = false;
                        break;
                    }
                }
            }
            
            if (pathMatches) {
                wordsFound.add(word);
                for (TextView tv : selectedCells) {
                    tv.setBackgroundResource(R.drawable.circle_cell_found);
                    tv.setTextColor(Color.WHITE);
                }
                Toast.makeText(this, R.string.found_word, Toast.LENGTH_SHORT).show();
                updateWordsToFindText();
                
                if (wordsFound.size() == currentWords.size()) {
                    streakCount++;
                    showStreakMessage();
                    new Handler(Looper.getMainLooper()).postDelayed(this::startNewGame, 2000);
                }
            } else {
                for (TextView tv : selectedCells) {
                    if (tv.getCurrentTextColor() != Color.WHITE) {
                        tv.setBackgroundResource(R.drawable.circle_cell);
                    }
                }
            }
        } else {
            for (TextView tv : selectedCells) {
                if (tv.getCurrentTextColor() != Color.WHITE) {
                    tv.setBackgroundResource(R.drawable.circle_cell);
                }
            }
        }
        lineDrawingView.clearLines();
        selectedCells.clear();
        currentSelection.setLength(0);
    }
    
    private void showStreakMessage() {
        tvStreak.setText(getString(R.string.streak_format, streakCount));
        
        if (streakCount == 1) {
            Toast.makeText(this, R.string.board_cleared, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.streak_message, streakCount), Toast.LENGTH_LONG).show();
        }
    }

}
