package com.aiphotographer.perception

import com.aiphotographer.model.BoundingBox

/** Aggregate geometry evidence only; it does not identify a person. */
class FaceContinuityProbe {
    private var previous: BoundingBox? = null
    private var lostAfterDetection = false
    var comparablePairs: Long = 0
        private set
    var disjointPairs: Long = 0
        private set
    var reacquisitions: Long = 0
        private set

    fun observe(box: BoundingBox?) {
        if (box == null) {
            if (previous != null) lostAfterDetection = true
            previous = null
            return
        }
        if (lostAfterDetection) {
            reacquisitions++
            lostAfterDetection = false
        }
        val prior = previous
        if (prior != null && comparable(prior) && comparable(box)) {
            comparablePairs++
            if (prior.right <= box.left || box.right <= prior.left ||
                prior.bottom <= box.top || box.bottom <= prior.top) disjointPairs++
        }
        previous = box
    }

    fun breakChain() {
        previous = null
        lostAfterDetection = false
    }

    fun reset() {
        breakChain()
        comparablePairs = 0
        disjointPairs = 0
        reacquisitions = 0
    }

    private fun comparable(box: BoundingBox) = box.left.isFinite() && box.top.isFinite() &&
        box.right.isFinite() && box.bottom.isFinite() && box.right > box.left && box.bottom > box.top
}
