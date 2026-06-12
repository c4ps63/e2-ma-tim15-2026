package com.example.slagalicavpl.model;

public class AsocijacijePuzzle {

    /** cells[col][row] — 4 columns, 4 rows each (0-indexed). */
    public final String[][] cells;

    /** One solution per column (4 total). */
    public final String[] colSolutions;

    /** The final (overall) solution that all 4 columns hint at. */
    public final String finalSolution;

    public AsocijacijePuzzle(String[][] cells, String[] colSolutions, String finalSolution) {
        this.cells         = cells;
        this.colSolutions  = colSolutions;
        this.finalSolution = finalSolution;
    }
}
