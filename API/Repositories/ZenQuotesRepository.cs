using Microsoft.EntityFrameworkCore;
using System;
using System.Collections.Generic;
using System.Linq;
using ZenSource.Models;
using ZenSource.Controllers;

namespace ZenSource.Repositories
{
    public class ZenQuotesRepository
    {
        private ZenContext _ctx;

        private readonly int PAGE_SIZE = 6;

        public ZenQuotesRepository(ZenContext context)
        {
            _ctx = context;
        }

        public IEnumerable<ZenQuote> GetAll(string search = null, List<int> ids = null, List<int> tags = null, int? page = null, string language = null, bool? valid = null)
        {
            var query = _ctx.Set<ZenQuote>().OrderByDescending(z => z.CreatedOn).AsQueryable();

            if (search != null)
            {
                var possibleQuotesBySearch = _ctx.Set<ZenMessage>().Where(m => m.Message.ToLower().Contains(search.ToLower())).Select(m => m.IdZenQuote).ToList();
                query = query.Where(q => q.Author.ToLower().Contains(search.ToLower()) || possibleQuotesBySearch.Contains(q.Id));
            }

            if (language != null)
            {
                var possibleQuotesByLanguage = _ctx.Set<ZenMessage>().Where(m => m.Language.Code.Equals(language, StringComparison.OrdinalIgnoreCase)).Select(m => m.IdZenQuote).ToList();
                query = query.Where(q => possibleQuotesByLanguage.Contains(q.Id));
            }

            if (valid != null)
            {
                query = query.Where(q => q.Valid == valid.Value);
            }

            if (tags != null && tags.Count > 0)
            {
                var possibleQuotesByTag = _ctx.Set<ZenQuoteTag>().Where(t => tags.Contains(t.TagId)).Select(t => t.ZenQuoteId).ToList();
                query = query.Where(q => possibleQuotesByTag.Contains(q.Id));
            }

            if (ids != null && ids.Count > 0)
            {
                query = query.Where(q => ids.Contains(q.Id));
            }

            if (page != null)
            {
                int p = Convert.ToInt32(page) - 1;
                if (p < 0) p = 0;
                query = query
                    .Skip(p * PAGE_SIZE)
                    .Take(PAGE_SIZE);
            }

            query = query
                .Include(o => o.ZenMessages)
                .ThenInclude(o => o.Language)
                .Include(o => o.ZenQuoteTags)
                .ThenInclude(o => o.Tag)
                ;

            return query
                .ToList();

        }

        public ZenQuote GetById(int id)
        {
            return _ctx.ZenQuotes
                .Where(o => o.Id == id)
                .Include(o => o.ZenQuoteTags)
                .Include(o => o.ZenMessages)
                .ThenInclude(o => o.Language)
                .FirstOrDefault();
        }

        public int UpdateRate(int id, int like, int dislike)
        {
            // TODO: Use Optmistic Concurrency approach to be thread safe
            var quote = _ctx.Set<ZenQuote>().First(o => o.Id == id);

            if (like > 0) quote.Likes++;
            else if (like < 0) quote.Likes--;

            if (dislike > 0) quote.Dislikes++;
            else if (dislike < 0) quote.Dislikes--;

            return _ctx.SaveChanges();

        }

        public void Save(ZenQuote zenQuote)
        {
            _ctx.ZenQuotes.Add(zenQuote);
            _ctx.SaveChanges();
        }

        public List<int> GetQuoteIds(string l = "EN")
        {
            var possibleQuotesByLanguage = _ctx.Set<ZenMessage>().Where(m => m.Language.Code.Equals(l, StringComparison.OrdinalIgnoreCase)).Select(m => m.IdZenQuote).ToList();

            return _ctx.Set<ZenQuote>()
                .Where(o => possibleQuotesByLanguage.Contains(o.Id))
                .Select(o => o.Id)
                .ToList();
        }

        internal void ValidateQuote(ZenController.ZenQuoteValidateModel quote)
        {

            var languages = _ctx.Languages.ToList();

            if (_ctx.ZenMessages.Any(m => m.IdZenQuote == quote.Id && m.Language.Code == "EN"))
            {
                _ctx.ZenMessages.First(m => m.IdZenQuote == quote.Id && m.Language.Code == "EN").Message = quote.En;
            }
            else
            {
                _ctx.ZenMessages.Add(new ZenMessage
                {
                    IdZenQuote = quote.Id,
                    IdLanguage = languages.First(l => l.Code == "En").Id,
                    Message = quote.En
                });
            }

            if (_ctx.ZenMessages.Any(m => m.IdZenQuote == quote.Id && m.Language.Code == "PT-BR"))
            {
                _ctx.ZenMessages.First(m => m.IdZenQuote == quote.Id && m.Language.Code == "PT-BR").Message = quote.En;
            }
            else
            {
                _ctx.ZenMessages.Add(new ZenMessage
                {
                    IdZenQuote = quote.Id,
                    IdLanguage = languages.First(l => l.Code == "PT-BR").Id,
                    Message = quote.En
                });
            }

            var zenQuote = _ctx.ZenQuotes.First(z => z.Id == quote.Id);
            zenQuote.Author = quote.Author;
            zenQuote.Valid = true;

            _ctx.SaveChanges();
        }
    }
}
