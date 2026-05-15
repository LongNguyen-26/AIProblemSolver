#include <bits/stdc++.h>
using namespace std;

struct Interval {
    int l, r; // inclusive
    char c;
    Interval(int _l=0,int _r=0,char _c='A'):l(_l),r(_r),c(_c){}
};

int main(){
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    int n,q;
    if(!(cin>>n>>q)) return 0;
    // initially one interval [0,n-1] with 'A'
    map<int, Interval> mp; // key = left endpoint
    mp[0]=Interval(0,n-1,'A');

    auto split = [&](int pos){
        // ensure there is an interval starting at pos
        if(pos>n-1) return;
        auto it = mp.upper_bound(pos);
        if(it==mp.begin()) return; // shouldn't happen
        --it;
        Interval cur = it->second;
        if(cur.l==pos) return; // already a split
        if(cur.r<pos) return; // out of range
        // split into [cur.l, pos-1] and [pos, cur.r]
        it->second.r = pos-1;
        mp[pos]=Interval(pos,cur.r,cur.c);
    };

    for(int i=0;i<q;i++){
        int type;cin>>type;
        if(type==1){
            int l,r;char c;cin>>l>>r>>c;
            split(l);
            split(r+1);
            // erase intervals fully inside [l,r]
            auto it = mp.lower_bound(l);
            vector<int> toErase;
            while(it!=mp.end() && it->second.l<=r){
                toErase.push_back(it->first);
                ++it;
            }
            for(int key:toErase) mp.erase(key);
            // insert new interval
            mp[l]=Interval(l,r,c);
        }else if(type==2){
            int k;cin>>k;
            long long ans=0;
            for(const auto &p: mp){
                int len = p.second.r - p.second.l + 1;
                if(len>k) ans += (len - k);
            }
            cout<<ans<<"\n";
        }
    }
    return 0;
}
