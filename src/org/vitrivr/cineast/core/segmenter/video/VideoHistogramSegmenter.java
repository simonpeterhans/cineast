package org.vitrivr.cineast.core.segmenter.video;

import org.vitrivr.cineast.core.data.*;
import org.vitrivr.cineast.core.data.entities.SegmentDescriptor;
import org.vitrivr.cineast.core.data.frames.VideoFrame;
import org.vitrivr.cineast.core.data.segments.SegmentContainer;
import org.vitrivr.cineast.core.data.segments.VideoSegment;
import org.vitrivr.cineast.core.decode.general.Decoder;
import org.vitrivr.cineast.core.decode.subtitle.SubTitle;
import org.vitrivr.cineast.core.segmenter.FuzzyColorHistogramCalculator;
import org.vitrivr.cineast.core.segmenter.general.Segmenter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author rgasser
 * @version 1.0
 * @created 17.01.17
 */
public class VideoHistogramSegmenter implements Segmenter<VideoFrame> {

    private static final double THRESHOLD = 0.05;

    private static final int SEGMENT_QUEUE_LENGTH = 10;

    private static final int PRESHOT_QUEUE_LENGTH = 10;

    private static final int MAX_SHOT_LENGTH = 720;

    private static final int SEGMENT_POLLING_TIMEOUT = 1000;


    private Decoder<VideoFrame> decoder;

    private LinkedList<VideoFrame> videoFrameList = new LinkedList<>();

    private LinkedList<Pair<VideoFrame,Double>> preShotList = new LinkedList<>();

    private LinkedBlockingQueue<SegmentContainer> segments = new LinkedBlockingQueue<>(SEGMENT_QUEUE_LENGTH);

    private ArrayList<SubTitle> subtitles = new ArrayList<SubTitle>();

    private List<SegmentDescriptor> knownShotBoundaries;

    private volatile boolean complete = false;

    private volatile boolean isrunning = false;

    /**
     *
     */
    public VideoHistogramSegmenter() {
        this.knownShotBoundaries = new LinkedList<SegmentDescriptor>();
    }

    /**
     *
     * @param knownShotBoundaries
     */
    public VideoHistogramSegmenter(List<SegmentDescriptor> knownShotBoundaries){
        this.knownShotBoundaries = ((knownShotBoundaries == null) ? new LinkedList<SegmentDescriptor>() : knownShotBoundaries);
    }

    /**
     *
     * @param st
     */
    public void addSubTitle(SubTitle st) {
        this.subtitles.add(st);
    }


    /**
     *
     * @param f
     * @return
     */
    private static Histogram getHistogram(VideoFrame f){
        return FuzzyColorHistogramCalculator.getSubdividedHistogramNormalized(f.getImage().getThumbnailImage(), 3);
    }

    /**
     * Method used to initialize the Segmenter - assigns the new Decoder instance and clears
     * all the queues.
     *
     * @param decoder Decoder used for media-decoding.
     */
    @Override
    public synchronized void init(Decoder<VideoFrame> decoder) {
        if (!this.isrunning) {
            this.decoder = decoder;
            this.complete = false;
            this.preShotList.clear();
            this.segments.clear();
            this.videoFrameList.clear();
        }
    }

    /**
     *
     * @return
     */
    public SegmentContainer getNext() throws InterruptedException {
        SegmentContainer nextContainer = this.segments.poll(SEGMENT_POLLING_TIMEOUT, TimeUnit.MILLISECONDS);
        if (nextContainer == null) {
            synchronized (this) {
                this.complete = !this.isrunning && this.decoder.complete();
            }
        }
        return nextContainer;
    }

    /**
     * Indicates whether the Segmenter is complete i.e. no new segments
     * are to be expected.
     */
    @Override
    public synchronized boolean complete() {
        return this.complete;
    }

    /**
     *
     */
    @Override
    public synchronized void close() {
        if (!this.isrunning) {
            this.decoder.close();
        }
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        /* Begin: Set running to false. */
        synchronized(this) {
            this.isrunning = true;
        }

        while (!this.decoder.complete()) {
            if (this.videoFrameList.isEmpty()) queueFrames();

            VideoSegment _return = null;

            if (!preShotList.isEmpty()) {
                _return = new VideoSegment(this.decoder.count());
                while (!preShotList.isEmpty()) {
                    _return.addFrame(preShotList.removeFirst().first);
                }
            }

            if (this.videoFrameList.isEmpty()) {
                this.segments.offer(_return);
                continue; //no more shots to segment
            }

            if (_return == null) {
                _return = new VideoSegment(this.decoder.count());
            }


            VideoFrame videoFrame = this.videoFrameList.poll();

            SegmentDescriptor bounds = this.knownShotBoundaries.size() > 0 ? this.knownShotBoundaries.remove(0) : null;

            if (bounds != null && videoFrame.getId() >= bounds.getStart() && videoFrame.getId() <= bounds.getEnd()) {

                _return.addFrame(videoFrame);
                queueFrames(bounds.getEnd() - bounds.getStart());
                do {
                    videoFrame = this.videoFrameList.poll();
                    if (videoFrame != null) {
                        _return.addFrame(videoFrame);
                    } else {
                        break;
                    }
                } while (videoFrame.getId() < bounds.getEnd());

                //addSubtitleItems(_return);

                this.segments.offer(_return);
                continue;

            } else {
                Histogram hPrev, h = getHistogram(videoFrame);
                _return.addFrame(videoFrame);
                while (true) {
                    if ((videoFrame = this.videoFrameList.poll()) == null) {
                        queueFrames();
                        if ((videoFrame = this.videoFrameList.poll()) == null) {
                            this.segments.offer(_return);
                            break;
                        }
                    }
                    hPrev = h;
                    h = getHistogram(videoFrame);
                    double distance = hPrev.getDistance(h);

                    preShotList.offer(new Pair<VideoFrame,Double>(videoFrame, distance));

                    if (preShotList.size() > PRESHOT_QUEUE_LENGTH) {
                        double max = 0;
                        int index = -1, i = 0;
                        for (Pair<VideoFrame, Double> pair : preShotList) {
                            if (pair.second > max) {
                                index = i;
                                max = pair.second;
                            }
                            i++;
                        }
                        if (max <= THRESHOLD && _return.getNumberOfFrames() < MAX_SHOT_LENGTH) { //no cut
                            for (Pair<VideoFrame, Double> pair : preShotList) {
                                _return.addFrame(pair.first);
                            }
                            preShotList.clear();
                        } else {
                            for (i = 0; i < index; ++i) {
                                _return.addFrame(preShotList.removeFirst().first);
                            }
                            break;
                        }
                    }
                }
                this.segments.offer(_return);
            }
        }

        /* End: Reset running to false. */
        synchronized(this) {
            this.isrunning = false;
        }
    }

    /**
     *
     * @return
     */
    private boolean queueFrames(){
        return queueFrames(20);
    }

    /**
     *
     * @param number
     * @return
     */
    private  boolean queueFrames(int number) {
        for(int i = 0; i < number; ++i){
            VideoFrame f = this.decoder.getNext();
            if (f == null) { //no more frames
                return false;
            } else {
                synchronized (this) {
                    this.videoFrameList.offer(f);
                }
            }
        }
        return true;
    }
}