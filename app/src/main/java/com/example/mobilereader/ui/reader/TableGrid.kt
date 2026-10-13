package com.example.mobilereader.ui.reader

import com.example.mobilereader.data.model.TableRow


object TableGrid {

    class Grid(
        val columnCount: Int,

        val cellColumns: List<IntArray>,

        val noBreakAfterRow: BooleanArray,
    )


    fun build(rows: List<TableRow>): Grid {
        val cellColumns = ArrayList<IntArray>(rows.size)
        val noBreak = BooleanArray(rows.size)
        var pending = IntArray(8)
        var columnCount = 0

        fun ensure(size: Int) {
            if (pending.size < size) pending = pending.copyOf(maxOf(size, pending.size * 2))
        }

        for ((rowIndex, row) in rows.withIndex()) {


            val columns = IntArray(row.cells.size)
            var col = 0
            val newSpans = mutableListOf<Pair<Int, Int>>()
            var newSpanRows = mutableListOf<Int>()
            for ((cellIndex, cell) in row.cells.withIndex()) {
                ensure(col + 1)
                while (col < pending.size && pending[col] > 0) col++
                columns[cellIndex] = col
                val span = cell.colSpan.coerceIn(1, 10)
                val rowSpan = cell.rowSpan.coerceIn(1, 20)
                ensure(col + span)
                if (rowSpan > 1) {
                    newSpans += col to (col + span)
                    newSpanRows += rowSpan - 1
                }
                col += span
                if (col > columnCount) columnCount = col
            }
            cellColumns += columns

            for (c in pending.indices) if (pending[c] > 0) pending[c]--

            for (i in newSpans.indices) {
                val (from, until) = newSpans[i]
                for (c in from until until) {
                    pending[c] = maxOf(pending[c], newSpanRows[i])
                }
            }

            noBreak[rowIndex] = pending.any { it > 0 }
        }
        return Grid(columnCount, cellColumns, noBreak)
    }


    fun distributeColumns(minW: IntArray, maxW: IntArray, availablePx: Int): IntArray {
        val n = minW.size
        if (n == 0) return IntArray(0)
        var sumMin = 0L
        var sumMax = 0L
        for (i in 0 until n) {
            sumMin += minW[i]
            sumMax += maxW[i]
        }
        if (sumMax <= availablePx) return maxW.copyOf()

        val out = IntArray(n)
        if (sumMin <= availablePx) {
            val slack = availablePx - sumMin
            val range = sumMax - sumMin
            if (range <= 0L) {
                for (i in 0 until n) out[i] = (availablePx.toLong() / n).toInt()
            } else {
                for (i in 0 until n) {
                    out[i] = (minW[i] + slack * (maxW[i] - minW[i]) / range).toInt()
                }
            }
        } else if (sumMin > 0L) {
            for (i in 0 until n) {
                out[i] = (availablePx.toLong() * minW[i] / sumMin).toInt()
            }
        } else {
            for (i in 0 until n) out[i] = (availablePx.toLong() / n).toInt()
        }

        var used = 0L
        for (w in out) used += w
        var i = 0
        while (used < availablePx) {
            out[i % n]++
            used++
            i++
        }
        return out
    }


    fun spreadSpan(into: IntArray, col: Int, span: Int, requiredPx: Int) {
        if (col >= into.size) return
        val end = minOf(col + span, into.size)
        if (end <= col) return
        var current = 0
        for (c in col until end) current += into[c]
        val deficit = requiredPx - current
        if (deficit <= 0) return
        val count = end - col
        val each = deficit / count
        var extra = deficit % count
        for (c in col until end) {
            into[c] += each + if (extra > 0) { extra--; 1 } else 0
        }
    }


    fun rowsThatFit(
        rowHeightsPx: IntArray,
        noBreakAfterRow: BooleanArray,
        from: Int,
        remainingPx: Int,
    ): Int {
        var used = 0L
        var end = from
        var lastSafe = from
        while (end < rowHeightsPx.size) {
            used += rowHeightsPx[end]
            if (used > remainingPx) break
            end++
            if (end >= rowHeightsPx.size || !noBreakAfterRow[end - 1]) lastSafe = end
        }
        return lastSafe
    }
}
