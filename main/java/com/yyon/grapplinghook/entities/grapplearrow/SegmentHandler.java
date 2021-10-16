package com.yyon.grapplinghook.entities.grapplearrow;

import java.util.LinkedList;

import com.yyon.grapplinghook.common.CommonSetup;
import com.yyon.grapplinghook.network.SegmentMessage;
import com.yyon.grapplinghook.utils.GrapplemodUtils;
import com.yyon.grapplinghook.utils.Vec;

import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.PacketDistributor;

public class SegmentHandler {

	public LinkedList<Vec> segments;
	public LinkedList<Direction> segmentbottomsides;
	public LinkedList<Direction> segmenttopsides;
	public World world;
	public GrapplehookEntity arrow;
	
	Vec prevhookpos = null;
	Vec prevplayerpos = null;;
	
	final double bendoffset = 0.05;
	final double intoblock = 0.05;
	
	public SegmentHandler(World w, GrapplehookEntity arrow, Vec hookpos, Vec playerpos) {
		segments = new LinkedList<Vec>();
		segments.add(hookpos);
		segments.add(playerpos);
		segmentbottomsides = new LinkedList<Direction>();
		segmentbottomsides.add(null);
		segmentbottomsides.add(null);
		segmenttopsides = new LinkedList<Direction>();
		segmenttopsides.add(null);
		segmenttopsides.add(null);
		this.world = w;
		this.arrow = arrow;
		this.prevhookpos = new Vec(hookpos);
		this.prevplayerpos = new Vec(playerpos);
		
//		System.out.println("segments:");
//		hookpos.print();
//		playerpos.print();
	}
	
	public void forceSetPos(Vec hookpos, Vec playerpos) {
		this.prevhookpos = new Vec(hookpos);
		this.prevplayerpos = new Vec(playerpos);
    	this.segments.set(0, new Vec(hookpos));
    	this.segments.set(this.segments.size() - 1, new Vec(playerpos));
	}
	
	double ropelen;
	
	public void updatepos(Vec hookpos, Vec playerpos, double ropelen) {
		segments.set(0, hookpos);
		segments.set(segments.size() - 1, playerpos);
		this.ropelen = ropelen;
	}
	
	public void update(Vec hookpos, Vec playerpos, double ropelen, boolean movinghook) {
		if (prevhookpos == null) {
	        prevhookpos = hookpos;
	        prevplayerpos = playerpos;
		}
		
		segments.set(0, hookpos);
		segments.set(segments.size() - 1, playerpos);
		this.ropelen = ropelen;
		
		
		Vec closest = segments.get(segments.size()-2);
		
		while (true) {
			if (segments.size() == 2) {
				break;
			}
			
			int index = segments.size()-2;
			closest = segments.get(index);
			Direction bottomside = segmentbottomsides.get(index);
			Direction topside = segmenttopsides.get(index);
			Vec ropevec = playerpos.sub(closest);
			
			Vec beforepoint = segments.get(index-1);
			
			Vec edgevec = getnormal(bottomside).cross(getnormal(topside));
			Vec planenormal = beforepoint.sub(closest).cross(edgevec);
//			planenormal = getnormal(bottomside).add(getnormal(topside)).proj(planenormal);
			
//			System.out.println(ropevec.dot(planenormal));
			
			if (ropevec.dot(planenormal) > 0) {
				this.removesegment(index);
			} else {
				break;
			}
		}
		
		Vec farthest = segments.get(1);
		
		if (movinghook) {
			while (true) {
				if (segments.size() == 2) {
					break;
				}
				
				int index = 1;
				farthest = segments.get(index);
				Direction bottomside = segmentbottomsides.get(index);
				Direction topside = segmenttopsides.get(index);
				Vec ropevec = farthest.sub(hookpos);
				
				Vec beforepoint = segments.get(index+1);
				
				Vec edgevec = getnormal(bottomside).cross(getnormal(topside));
				Vec planenormal = beforepoint.sub(farthest).cross(edgevec);
//				planenormal = getnormal(bottomside).add(getnormal(topside)).proj(planenormal);
				
//				System.out.println(ropevec.dot(planenormal));
				
				if (ropevec.dot(planenormal) > 0 || ropevec.length() < 0.1) {
//					System.out.println("removed farthest");
					this.removesegment(index);
				} else {
					break;
				}
			}
			
			while (true) {
				if (this.getDistToFarthest() > ropelen) {
					this.removesegment(1);
				} else {
					break;
				}
			}
		}
		
		if (movinghook) {
			farthest = segments.get(1);
			Vec prevfarthest = farthest;
			if (segments.size() == 2) {
				prevfarthest = prevplayerpos;
			}
			updatesegment(hookpos, prevhookpos, farthest, prevfarthest, 1, 0);
		}
		
		Vec prevclosest = closest;
		if (segments.size() == 2) {
			prevclosest = prevhookpos;
		}
		updatesegment(closest, prevclosest, playerpos, prevplayerpos, segments.size() - 1, 0);
		
		
        prevhookpos = hookpos;
        prevplayerpos = playerpos;
	}
	
	public void removesegment(int index) {
		segments.remove(index);
		segmentbottomsides.remove(index);
		segmenttopsides.remove(index);

		if (!this.world.isClientSide) {
			SegmentMessage addmessage = new SegmentMessage(this.arrow.getId(), false, index, new Vec(0, 0, 0), Direction.DOWN, Direction.DOWN);
			Vec playerpoint = Vec.positionvec(this.arrow.shootingEntity);
			CommonSetup.network.send(PacketDistributor.TRACKING_CHUNK.with(() -> world.getChunkAt(new BlockPos(playerpoint.x, playerpoint.y, playerpoint.z))), addmessage);
//			grapplemod.network.sendToAllAround(addmessage, new TargetPoint(this.world.provider.getDimension(), playerpoint.x, playerpoint.y, playerpoint.z, 100));
		}
	}
	
	public void updatesegment(Vec top, Vec prevtop, Vec bottom, Vec prevbottom, int index, int numberrecursions) {		
		BlockRayTraceResult bottomraytraceresult = GrapplemodUtils.rayTraceBlocks(this.world, bottom, top);
        
        // if rope hit block
        if (bottomraytraceresult != null) {
        	if (GrapplemodUtils.rayTraceBlocks(this.world, prevbottom, prevtop) != null) {
//        		System.out.println("Warning: prev collision");
        		return;
        	}
        	
//        	System.out.println(bottomraytraceresult.typeOfHit);
            Vec bottomhitvec = new Vec(bottomraytraceresult.getLocation());
/*            this.arrow.debugpos = bottomhitvec;
            this.arrow.debugpos2 = bottom;
            this.arrow.debugpos3 = top;*/
            Direction bottomside = bottomraytraceresult.getDirection();
            Vec bottomnormal = this.getnormal(bottomside);
            
            // calculate where bottomhitvec was along the rope in the previous tick
//            double ropelen = top.sub(bottom).length();
            double prevropelen = prevtop.sub(prevbottom).length();
            
//            double bottomtohit = bottom.sub(bottomhitvec).length();
//            double prevbottomtohit = bottomtohit * ropelen / prevropelen;
            
//            vec prevbottomhit = prevtop.sub(prevbottom).changelen(prevbottomtohit).add(prevbottom);
            
            // use prevbottomhit to calculate the velocity of that part of the rope when it hit the block
 //           vec motionalonghit = bottomhitvec.sub(prevbottomhit);
            
            // calculate the motion parallel to the block side
//            vec motionparallel = motionalonghit.removealong(bottomnormal);
            
            // the rope must have hit the corner on the plane across the edge of the block
            // and is bounded by the quadrilateral top, prevtop, prevbottom, bottom
            Vec cornerbound1 = bottomhitvec.add(bottomnormal.changelen(-intoblock));
            
//            vec cornerbound2 = null;
//            double cornerlinedist = Double.POSITIVE_INFINITY;
            
            Vec bound_option1 = line_plane_intersection(prevtop, prevbottom, cornerbound1, bottomnormal);
/*            if (cornerbound1.sub(bound_option1).length() < cornerlinedist) {
            	cornerbound2 = bound_option1;
            	cornerlinedist = cornerbound1.sub(bound_option1).length();
            }*/
            Vec bound_option2 = line_plane_intersection(top, prevtop, cornerbound1, bottomnormal);
/*            if (cornerbound1.sub(bound_option2).length() < cornerlinedist) {
            	cornerbound2 = bound_option2;
            	cornerlinedist = cornerbound1.sub(bound_option2).length();
            }*/
            Vec bound_option3 = line_plane_intersection(prevbottom, bottom, cornerbound1, bottomnormal);
/*            if (cornerbound1.sub(bound_option3).length() < cornerlinedist) {
            	cornerbound2 = bound_option3;
            	cornerlinedist = cornerbound1.sub(bound_option3).length();
            }*/
            
//            if (cornerbound2 != null) {
            for (Vec cornerbound2 : new Vec[] {bound_option1, bound_option2, bound_option3}) {
            	if (cornerbound2 == null) {
            		continue;
            	}
            	
            	// the corner must be in the line (cornerbound2, cornerbound1)
            	BlockRayTraceResult cornerraytraceresult = GrapplemodUtils.rayTraceBlocks(this.world, cornerbound2, cornerbound1);
                if (cornerraytraceresult != null) {
                	Vec cornerhitpos = new Vec(cornerraytraceresult.getLocation());
                	Direction cornerside = cornerraytraceresult.getDirection();
                	
                	if (cornerside == bottomside || 
                			cornerside.getOpposite() == bottomside) {
                		// this should not happen
//                		System.out.println("Warning: corner is same or opposite of bottomside");
                		continue;
                	} else {
                		// add a bend around the corner
                		Vec actualcorner = cornerhitpos.add(bottomnormal.changelen(intoblock));
                		Vec bend = actualcorner.add(bottomnormal.changelen(bendoffset)).add(getnormal(cornerside).changelen(bendoffset));
                		Vec topropevec = bend.sub(top);
                		Vec bottomropevec = bend.sub(bottom);
                		
                		// ignore bends that are too close to another bend
                		if (topropevec.length() < 0.05) {
                			if (this.segmentbottomsides.get(index - 1) == bottomside && this.segmenttopsides.get(index - 1) == cornerside) {
//                    			System.out.println("Warning: top bend is too close");
                    			continue;
                			}
                		}
                		if (bottomropevec.length() < 0.05) {
                			if (this.segmentbottomsides.get(index) == bottomside && this.segmenttopsides.get(index) == cornerside) {
//                    			System.out.println("Warning: bottom bend is too close");
                    			continue;
                			}
                		}
                		
                		this.actuallyaddsegment(index, bend, bottomside, cornerside);
                		
                		// if not enough rope length left, undo
                		if(this.getDistToAnchor() + .2 > this.ropelen) {
//                			System.out.println("Warning: not enough length left, removing");
                			this.removesegment(index);
                			continue;
                		}
                		
                		// now to recurse on top section of rope
                		double newropelen = topropevec.length() + bottomropevec.length();
                		
                		double prevtoptobend = topropevec.length() * prevropelen / newropelen;
                		Vec prevbend = prevtop.add(prevbottom.sub(prevtop).changelen(prevtoptobend));
                		
                		if (numberrecursions < 10) {
                    		updatesegment(top, prevtop, bend, prevbend, index, numberrecursions+1);
                		} else {
                			System.out.println("Warning: number recursions exceeded");
                		}
                		break;
                	}
//                } else {
//                	System.out.println("Warning: no corner collision");
                }
//            } else {
//            	System.out.println("Warning: cornerbound2 is null");
            }
            
            
            
/*            RayTraceResult topraytraceresult = grapplemod.rayTraceBlocks(this.world, top, bottom(topraytraceresult.hitVec.x, topraytraceresult.hitVec.y, topraytraceresult.hitVec.z);
            Direction topside = topraytraceresult.sideHit;
            
            if (bottomhitvec.sub(top).length() > 0.01 && tophitvec.sub(bottom).length() > 0.01) {
            	if (bottomside == topside) {
            		System.out.println("Warning: bottomside == topside");
            	} else if ((bottomside == Direction.DOWN && topside == Direction.UP) || 
	            		(bottomside == Direction.UP && topside == Direction.DOWN) || 
	            		(bottomside == Direction.EAST && topside == Direction.WEST) || 
	            		(bottomside == Direction.WEST && topside == Direction.EAST) || 
	            		(bottomside == Direction.NORTH && topside == Direction.SOUTH) || 
	            		(bottomside == Direction.SOUTH && topside == Direction.NORTH)) {
	            	System.out.println("two sides");
	            	// binary search to find 3rd side
	            	vec newprevtop = prevtop;
	            	vec newprevbottom = prevbottom;
            		vec center = bottomhitvec.add(tophitvec).mult(0.5);
	            	for (int i = 0; i < 20; i++) {
	            		vec prevcenter = newprevtop.add(newprevbottom).mult(0.5);
	            		RayTraceResult thirdsidetrace = grapplemod.rayTraceBlocks(this.world, prevcenter, center {
	            			break;
	            		}
	            		Direction thirdside = thirdsidetrace.sideHit;
	            		if (thirdside == bottomside) {
	            			newprevbottom = prevcenter;
	            		} else if (thirdside == topside) {
	            			newprevtop = prevcenter;
	            		} else {
	            			vec collisionpoint = new vec(thirdsidetrace.hitVec.x, thirdsidetrace.hitVec.y, thirdsidetrace.hitVec.z);
	            			this.addsegment(bottomhitvec, collisionpoint, bottomside, thirdside, index, top, prevtop, bottom, prevbottom);
	            			this.addsegment(collisionpoint, tophitvec, thirdside, topside, index, top, prevtop, bottom, prevbottom);
	            			break;
	            		}
	            	}
	            } else {
                    this.addsegment(bottomhitvec, tophitvec, bottomside, topside, index, top, prevtop, bottom, prevbottom);
	            }
            }*/
        }
	}
	
	public Vec line_plane_intersection(Vec linepoint1, Vec linepoint2, Vec planepoint, Vec planenormal) {
		// calculate the intersection of a line and a plane
		// formula: https://en.wikipedia.org/wiki/Line%E2%80%93plane_intersection#Algebraic_form
		
		Vec linevec = linepoint2.sub(linepoint1);
		
		if (linevec.dot(planenormal) == 0) {
			return null;
		}
		
		double d = planepoint.sub(linepoint1).dot(planenormal) / linevec.dot(planenormal);
		return linepoint1.add(linevec.mult(d));
	}
	
	public Vec getnormal(Direction facing) {
		Vector3i facingvec = facing.getNormal();
		return new Vec(facingvec.getX(), facingvec.getY(), facingvec.getZ());
	}
	
	public boolean hookpastbend(double ropelen) {
		return (this.getDistToFarthest() > ropelen);
	}
	
	public BlockPos getbendblock(int index) {
		Vec bendpos = this.segments.get(index);
		bendpos.add_ip(this.getnormal(this.segmentbottomsides.get(index)).changelen(-this.intoblock * 2));
		bendpos.add_ip(this.getnormal(this.segmenttopsides.get(index)).changelen(-this.intoblock * 2));
		return new BlockPos(bendpos.x, bendpos.y, bendpos.z);
	}
	
/*	public void addsegment(vec bottomhit, vec tophit, Direction bottomside, Direction topside, int index, vec top, vec prevtop, vec bottom, vec prevbottom) {
		System.out.println("Computing bend point");
		
		vec bottomnormal = getnormal(bottomside);
		vec topnormal = getnormal(topside);
		vec edgevec = bottomnormal.cross(topnormal);
		
		edgevec.print();
		
		double d = (tophit.sub(bottomhit)).dot(topnormal) / topnormal.dot(topnormal);
		vec edgepoint = topnormal.mult(d).add(bottomhit);
		
		edgepoint.print();
		
		vec movement = bottom.sub(prevbottom);
		if (movement.length() == 0) {
			movement = top.sub(prevtop);
		}
		vec planenormal = movement.cross(top.sub(bottom));
		
		movement.print();
		planenormal.print();
		
		if (edgevec.dot(planenormal) == 0) {
			System.out.println("warning: can't compute bend point");
			return;
		}
		
		double d2 = (top.sub(edgepoint)).dot(planenormal) / edgevec.dot(planenormal);
		
		vec intersectionpoint = edgevec.mult(d2).add(edgepoint);
		
		intersectionpoint.print();
		
		vec offset = bottomnormal.add(topnormal).mult(0.1);
		vec bendpoint = intersectionpoint.add(offset);
		
		this.actuallyaddsegment(index, bendpoint, bottomside, topside);

		if(this.getDistToAnchor() + .2 > this.ropelen) {
			System.out.println("not enough length left, removing");
			this.removesegment(index);
			return;
		}
	}*/
	
	public void actuallyaddsegment(int index, Vec bendpoint, Direction bottomside, Direction topside) {
        segments.add(index, bendpoint);
        segmentbottomsides.add(index, bottomside);
        segmenttopsides.add(index, topside);
        
        /*System.out.println("added segment");
		this.print();*/
		
		if (!this.world.isClientSide) {
			SegmentMessage addmessage = new SegmentMessage(this.arrow.getId(), true, index, bendpoint, topside, bottomside);
			Vec playerpoint = Vec.positionvec(this.arrow.shootingEntity);
			CommonSetup.network.send(PacketDistributor.TRACKING_CHUNK.with(() -> world.getChunkAt(new BlockPos(playerpoint.x, playerpoint.y, playerpoint.z))), addmessage);
//			grapplemod.network.sendToAllAround(addmessage, new TargetPoint(this.world.provider.getDimension(), playerpoint.x, playerpoint.y, playerpoint.z, 100));
		}
	}
	
	public void print() {
		for (int i = 1; i < segments.size() - 1; i++) {
			System.out.print(i);
			System.out.print(" ");
			System.out.print(segmenttopsides.get(i).toString());
			System.out.print(" ");
			System.out.print(segmentbottomsides.get(i).toString());
			System.out.print(" ");
			segments.get(i).print();
		}
	}
	
	public Vec getclosest(Vec hookpos) {
		segments.set(0, hookpos);
		
		return segments.get(segments.size() - 2);
	}
	
	public double getDistToAnchor() {
		double dist = 0;
		for (int i = 0; i < segments.size() - 2; i++) {
			dist += segments.get(i).sub(segments.get(i+1)).length();
		}
		
		return dist;
	}
	
	public Vec getfarthest() {
		return segments.get(1);
	}
	
	public double getDistToFarthest() {
		double dist = 0;
		for (int i = 1; i < segments.size() - 1; i++) {
			dist += segments.get(i).sub(segments.get(i+1)).length();
		}
		
		return dist;
	}
	
	public double getDist(Vec hookpos, Vec playerpos) {
		segments.set(0, hookpos);
		segments.set(segments.size() - 1, playerpos);
		double dist = 0;
		for (int i = 0; i < segments.size() - 1; i++) {
			dist += segments.get(i).sub(segments.get(i+1)).length();
		}
		
		return dist;
	}
	
	public AxisAlignedBB getBoundingBox(Vec hookpos, Vec playerpos) {
		this.updatepos(hookpos, playerpos, this.ropelen);
		Vec minvec = new Vec(hookpos);
		Vec maxvec = new Vec(hookpos);
		for (int i = 1; i < segments.size(); i++) {
			Vec segpos = segments.get(i);
			if (segpos.x < minvec.x) {
				minvec.x = segpos.x;
			} else if (segpos.x > maxvec.x) {
				maxvec.x = segpos.x;
			}
			if (segpos.y < minvec.y) {
				minvec.y = segpos.y;
			} else if (segpos.y > maxvec.y) {
				maxvec.y = segpos.y;
			}
			if (segpos.z < minvec.z) {
				minvec.z = segpos.z;
			} else if (segpos.z > maxvec.z) {
				maxvec.z = segpos.z;
			}
		}
		AxisAlignedBB bb = new AxisAlignedBB(minvec.x, minvec.y, minvec.z, maxvec.x, maxvec.y, maxvec.z);
//		System.out.print(bb.minX);
//		System.out.print(" ");
//		System.out.println(bb.maxX);
		return bb;
	}
}