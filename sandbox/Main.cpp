#include <bits/stdc++.h>
using namespace std;

struct Fenwick {
    int n; vector<int> bit;
    Fenwick(int n=0): n(n), bit(n+1,0) {}
    void add(int idx,int val){ for(++idx; idx<=n; idx+=idx&-idx) bit[idx]+=val; }
    int sumPrefix(int idx){ int r=0; for(++idx; idx>0; idx-=idx&-idx) r+=bit[idx]; return r; }
    int rangeSum(int l,int r){ if(l>r) return 0; return sumPrefix(r)- (l?sumPrefix(l-1):0); }
};

struct Interval{int r; char c;};

int main(){ios::sync_with_stdio(false);cin.tie(nullptr);
    int n,q; if(!(cin>>n>>q)) return 0;
    const int B = 550; // sqrt approx
    vector<long long> ansSmall(B+2,0); // indexed by d = k+1
    Fenwick ft(n+2);
    vector<int> cntLen(n+2,0);
    auto addLen = [&](int L,int delta){ // delta = +1 or -1
        if(L<=0) return;
        cntLen[L]+=delta;
        ft.add(L,delta);
        for(int d=1; d<=B; ++d) ansSmall[d] += (long long)delta * (L/d);
    };
    // initially all 'A' -> one interval [0,n-1]
    map<int,Interval> mp;
    mp[0] = {n-1,'A'};
    addLen(n,1);

    // split function
    auto split = [&](int pos){
        auto it = mp.lower_bound(pos);
        if(it!=mp.end() && it->first==pos) return it;
        --it; // it points to interval containing pos
        int l = it->first; int r = it->second.r; char c = it->second.c;
        if(pos>r) return mp.end();
        // split into [l,pos-1] and [pos,r]
        it->second.r = pos-1;
        mp[pos] = {r,c};
        return mp.find(pos);
    };

    for(int _=0;_<q;_++){
        int t;cin>>t;
        if(t==1){
            int l,r; char c;cin>>l>>r>>c;
            auto itR = split(r+1);
            auto itL = split(l);
            // erase intervals in [l,r]
            vector<pair<int,Interval>> toErase;
            for(auto it=itL; it!=itR; ++it) toErase.push_back(*it);
            for(auto &p: toErase){
                int L = p.first; int R = p.second.r; // length = R-L+1
                addLen(R-L+1,-1);
                mp.erase(L);
            }
            // possibly merge with left/right same char
            int newL = l, newR = r;
            // left neighbor
            auto itLeft = mp.lower_bound(l);
            if(itLeft!=mp.begin()){
                --itLeft;
                if(itLeft->second.c==c){
                    newL = itLeft->first;
                    addLen(itLeft->second.r - itLeft->first +1, -1);
                    mp.erase(itLeft);
                }
            }
            // right neighbor (after erasure, find first >= l)
            auto itRight = mp.lower_bound(r+1);
            if(itRight!=mp.end() && itRight->first==r+1 && itRight->second.c==c){
                newR = itRight->second.r;
                addLen(itRight->second.r - itRight->first +1, -1);
                mp.erase(itRight);
            }
            // insert merged interval
            mp[newL] = {newR,c};
            addLen(newR - newL +1, 1);
        }else if(t==2){
            int k;cin>>k; int d = k+1;
            long long ans=0;
            if(d<=B){ ans = ansSmall[d]; }
            else{
                int maxL = n;
                for(int mult=d; mult<=maxL; mult+=d){
                    int l = mult;
                    int r = min(maxL, mult + d -1);
                    int cnt = ft.rangeSum(l, r);
                    int qv = mult/d; // floor(L/d) = mult/d for this block
                    ans += (long long)cnt * qv;
                }
            }
            cout<<ans<<"\n";
        }
    }
    return 0; }
